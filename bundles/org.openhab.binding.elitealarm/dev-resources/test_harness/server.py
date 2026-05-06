import socket
import os
import threading
import http.server
import socketserver
import json
from urllib.parse import urlparse, parse_qs

# --- CONFIG ---
TCP_PORT = 9000
WEB_PORT = 8081
BINDING_PREFIX = "elitealarm"

# --- ALARM STATE ---
alarm_state = {
    "zones": [{"id": i, "unsealed": False, "alarm": False, "trouble": False} for i in range(1, 17)],
    "areas": [{"id": 1, "status": "D", "ready": True, "in_alarm": False}] + [{"id": i, "status": "D"} for i in range(2, 9)],
    "system": {
        "mains": "MR",
        "battery": "BR",
        "tamper": "TR",
        "fuse": "FR"
    },
    "authenticated": False,
    "auth_mode": "none",  # 'none' or 'user_pass'
    "timeout_active": False,
    "command_log": [],
    "device": 32,
    "mode": 4,
    "outputs": [{"id": i, "on": False} for i in range(1, 9)]
}

# --- TCP CLIENT ---
#
client_socket = None

def send_to_client(message):
    """Sends a message to the connected openHAB binding."""
    global client_socket, alarm_state
    if client_socket:
        try:
            client_socket.sendall(f"{message}\n".encode('utf-8'))
            print(f"-> Sent to client: {message}")
            log_entry = {"direction": "sent", "command": message}
            alarm_state["command_log"].append(log_entry)
            alarm_state["command_log"] = alarm_state["command_log"][-50:]

        except:
            print("Client disconnected.")
            client_socket = None

def send_area_readiness():
    """Sends the current readiness state for Area 1."""
    global alarm_state
    is_any_zone_unsealed = any(z['unsealed'] for z in alarm_state['zones'])
    is_ready_now = not is_any_zone_unsealed
    
    alarm_state['areas'][0]['ready'] = is_ready_now

    if is_ready_now:
        send_to_client("RO1") # Area 1 Ready (Sealed)
    else:
        send_to_client("NR1") # Area 1 Not Ready

def send_area_alarm_status():
    """Sends the current alarm state for Area 1."""
    global alarm_state
    is_any_zone_in_alarm = any(z['alarm'] for z in alarm_state['zones'])

    if is_any_zone_in_alarm:
        alarm_state['areas'][0]['in_alarm'] = True
        send_to_client("AA1")  # Area 1 in Alarm
    else:
        alarm_state['areas'][0]['in_alarm'] = False
        send_to_client("AR1")  # Area 1 Alarm Restore


def handle_client(conn, addr):
    """Handle a new TCP client connection."""
    global client_socket, alarm_state
    print(f"Accepted connection from {addr}")
    client_socket = conn
    alarm_state["timeout_active"] = False

    # --- Authentication Flow ---
    if alarm_state["auth_mode"] == "user_pass":
        conn.sendall(b"Username:\n")
        user = conn.recv(1024).decode('utf-8').strip()
        conn.sendall(b"Password:\n")
        pwd = conn.recv(1024).decode('utf-8').strip()
        if user == "admin" and pwd == "1234":
            alarm_state["authenticated"] = True
            conn.sendall(b"Welcome\n")
            print("Client authenticated successfully.")
        else:
            print("Client failed authentication.")
            conn.close()
            client_socket = None
            return
    else: # No auth
        alarm_state["authenticated"] = True
        conn.sendall(b"Welcome\n")
        print("Client connected (no auth).")


    # --- Command Loop ---
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break
            message = data.decode('utf-8').strip()
            print(f"<- Received from client: {message}")

            log_entry = {"direction": "received", "command": message}
            alarm_state["command_log"].append(log_entry)

            # Respond to commands
            if message == "STATUS":
                if alarm_state.get("timeout_active", False):
                    print("<- Received STATUS, but timeout is active. Ignoring.")
                    continue # Just loop, don't respond
                
                send_to_client("OK Status") # Generic ACK

                # Dump Zone States (only non-normal)
                for zone in alarm_state["zones"]:
                    if zone['unsealed']:
                        send_to_client(f"ZO{zone['id']}")
                    if zone['alarm']:
                        send_to_client(f"ZA{zone['id']}")
                    if zone['trouble']:
                        send_to_client(f"ZT{zone['id']}")

                # Dump System States (only non-normal)
                for val in alarm_state["system"].values():
                    if val not in ["MR", "BR", "TR", "FR"]:
                        send_to_client(val)

                # Dump Output States (only 'on' states)
                for output in alarm_state["outputs"]:
                    if output['on']:
                        send_to_client(f"OO{output['id']}")
                
                # Always send area readiness
                send_area_readiness()
                send_area_alarm_status()

            elif message.startswith("VERSION"):
                send_to_client('OK Version "TestHarness FW Ver. 1.0.0"')
            elif message.startswith("MODE"):
                send_to_client(f"OK MODE {alarm_state['mode']}")
            elif message.startswith("DEVICE"):
                send_to_client(f"OK Device {alarm_state['device']}")

            elif message == "EXIT":
                print("<- Received EXIT from client. Closing connection.")
                break

    except ConnectionResetError:
        print(f"Client {addr} disconnected.")
    finally:
        conn.close()
        client_socket = None

def run_tcp_server():
    """Run the TCP server to listen for the binding."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(('', TCP_PORT))
        s.listen()
        print(f"TCP server listening on port {TCP_PORT}")
        while True:
            conn, addr = s.accept()
            client_thread = threading.Thread(target=handle_client, args=(conn, addr))
            client_thread.daemon = True
            client_thread.start()

# --- WEB SERVER for UI control ---
class ControlHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        super().end_headers()

    def do_GET(self):
        if self.path == '/favicon.ico':
            self.send_response(204)
            self.end_headers()
            return

        if self.path == '/':
            self.path = '/index.html'

        if self.path == '/api/state':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(alarm_state).encode('utf-8'))
            return
        
        if self.path == '/api/command_log':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"command_log": alarm_state["command_log"]}).encode('utf-8'))
            return

        return http.server.SimpleHTTPRequestHandler.do_GET(self)

    def do_POST(self):
        if self.path.startswith("/api/"):
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            body = json.loads(post_data)

            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            response = {"status": "ok"}
            
            # --- API Logic ---
            if self.path == "/api/set_zone_status":
                zone_id = int(body['id'])
                attr = body['attribute']
                state = body['state']
                alarm_state["zones"][zone_id-1][attr] = state
                
                code = ""
                if attr == "unsealed": 
                    code = "ZO" if state else "ZC"
                    send_to_client(f"{code}{zone_id}")
                    send_area_readiness() 
                elif attr == "alarm":    
                    code = "ZA" if state else "ZR"
                    send_to_client(f"{code}{zone_id}")
                    send_area_alarm_status()
                elif attr == "trouble":  
                    code = "ZT" if state else "ZTR"
                    send_to_client(f"{code}{zone_id}")

                response['message'] = f"Zone {zone_id} {attr} set to {state}"

            elif self.path == "/api/set_system_status":
                component = body['component']
                new_status = body['status']
                alarm_state["system"][component] = new_status
                send_to_client(new_status)
                response['message'] = f"System {component} status set to {new_status}"
            
            elif self.path == "/api/set_output_status":
                output_id = int(body['id'])
                state = body['state']
                alarm_state["outputs"][output_id - 1]['on'] = state
                
                code = "OO" if state else "OR"
                send_to_client(f"{code}{output_id}")
                
                response['message'] = f"Output {output_id} set to {'On' if state else 'Off'}"

            
            elif self.path == "/api/set_auth_mode":
                alarm_state["auth_mode"] = body['mode']
                response['message'] = f"Auth mode set to {body['mode']}"

            elif self.path == "/api/set_device_mode":
                if 'device' in body:
                    new_device = int(body['device'])
                    if 1 <= new_device <= 32:
                        alarm_state['device'] = new_device
                        response['message'] = f"Device set to {new_device}"
                    else:
                        response['status'] = 'error'
                        response['message'] = "Device value out of range (1-32)"
                
                if 'mode' in body:
                    new_mode = int(body['mode'])
                    if 1 <= new_mode <= 4:
                        alarm_state['mode'] = new_mode
                        response['message'] = f"Mode set to {new_mode}"
                    else:
                        response['status'] = 'error'
                        response['message'] = "Mode value out of range (1-4)"


            elif self.path == "/api/disconnect":
                global client_socket
                if client_socket:
                    try:
                        client_socket.shutdown(socket.SHUT_RDWR)
                        client_socket.close()
                    except OSError:
                        pass # Already closed
                    client_socket = None
                    response['message'] = "Client disconnected."
                    print("Client disconnect requested from UI.")
                else:
                    response['message'] = "No client connected."
            
            elif self.path == "/api/toggle_timeout":
                alarm_state["timeout_active"] = not alarm_state["timeout_active"]
                response['message'] = f"Timeout simulation set to {alarm_state['timeout_active']}"

            elif self.path == "/api/send_oversized_packet":
                if client_socket:
                    try:
                        # Send 5000 characters without a newline to trip the 4096 safety valve
                        client_socket.sendall(("X" * 5000).encode('utf-8'))
                        alarm_state["command_log"].append({"direction": "sent", "command": "<OVERSIZED DATA>"})
                        response['message'] = "Sent oversized packet (no newline)."
                    except Exception as e:
                        response['status'] = 'error'
                        response['message'] = str(e)
                else:
                    response['status'] = "error"
                    response['message'] = "No client connected."

            elif self.path == "/api/send_empty_packet":
                if client_socket:
                    try:
                        client_socket.sendall(b"\n")
                        alarm_state["command_log"].append({"direction": "sent", "command": "<EMPTY PACKET>"})
                        response['message'] = "Sent empty packet (only newline)."
                    except Exception as e:
                        response['status'] = 'error'
                        response['message'] = str(e)
                else:
                    response['status'] = "error"
                    response['message'] = "No client connected."

            elif self.path == "/api/send_unterminated_packet":
                if client_socket:
                    try:
                        client_socket.sendall(b"ZO1")
                        alarm_state["command_log"].append({"direction": "sent", "command": "ZO1 (No Newline)"})
                        response['message'] = "Sent unterminated packet."
                    except Exception as e:
                        response['status'] = 'error'
                        response['message'] = str(e)
                else:
                    response['status'] = "error"
                    response['message'] = "No client connected."

            elif self.path == "/api/exit":
                response['message'] = "Server shutting down"
                shutdown_flag.set()

            self.wfile.write(json.dumps(response).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

httpd = None
shutdown_flag = threading.Event()

def run_web_server():
    """Run the web server for UI control."""
    global httpd
    socketserver.TCPServer.allow_reuse_address = True
    httpd = socketserver.TCPServer(("", WEB_PORT), ControlHandler)
    web_thread = threading.Thread(target=httpd.serve_forever)
    web_thread.daemon = True
    web_thread.start()
    print(f"Web server for UI control listening on http://localhost:{WEB_PORT}")
    
    shutdown_flag.wait()
    
    print("Shutting down web server...")
    httpd.shutdown()
    httpd.server_close()

if __name__ == "__main__":
    # Run TCP server in a background thread
    tcp_thread = threading.Thread(target=run_tcp_server)
    tcp_thread.daemon = True
    tcp_thread.start()

    # Run web server in the main thread and wait for shutdown
    run_web_server()
