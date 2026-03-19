# EliteAlarm Test Harness

This test harness emulates an EliteAlarm control panel for testing the openHAB binding without requiring a physical device.

It consists of two parts:
- A TCP server that listens on port 9000 and communicates with the binding.
- A web-based UI that allows you to control the state of the simulated alarm panel.

## How to Use

1.  **Run the Server:**
    Navigate to this directory in your terminal and run the server:
    ```bash
    python3 server.py
    ```
    This will start the TCP server on port 9000 and the web server on port 8080.

2.  **Configure the Binding:**
    In openHAB, configure the EliteAlarm bridge to connect to the test harness:
    -   **Host:** `127.0.0.1`
    -   **Port:** `9000`

3.  **Control the Simulation:**
    Open your web browser and go to `http://localhost:8081`.
    From this page, you can:
    -   Set the authentication mode (no auth or username/password).
    -   Simulate system status changes (mains fail, battery fail, etc.).
    -   Simulate zone status changes (sealed, unsealed, alarm, tamper).

Any changes you make in the web UI will be sent to the openHAB binding over the TCP connection.

## Extending the Harness

This is a basic implementation. You can extend it by:
- Adding controls for Areas and Outputs in `index.html`.
- Expanding the API in `server.py` to handle these new controls.
- Adding more detailed and unsolicited messages to be sent to the binding to simulate a real panel more closely.
