import requests
import time
import json
import os

# --- CONFIG ---
script_dir = os.path.dirname(os.path.realpath(__file__))
config_path = os.path.join(script_dir, 'config.json')

with open(config_path) as f:
    config = json.load(f)
OH_URL = config['OH_URL']
API_TOKEN = config['API_TOKEN']

HEADERS = {"Authorization": f"Bearer {API_TOKEN}", "Content-Type": "application/json"}
BINDING_PREFIX = "elitealarm"
PAGE_ID = "elite_dashboard"

link_queue = []

def request(method, path, body=None):
    return requests.request(method, f"{OH_URL}/{path}", headers=HEADERS, json=body)

# --- 1. CLEANUP ---
def cleanup():
    print("🧹 Cleaning up existing Elite Alarm artifacts...")
    
     # 1. DELETE UI PAGE
    # Path: /ui/components/ui%3Apage/{ID}
    target_page_id = PAGE_ID
    delete_url = f"ui/components/ui%3Apage/{target_page_id}"
    
    resp = request("DELETE", delete_url)
    
    if resp.status_code == 200:
        print(f"  🗑️ Deleted UI Page: {PAGE_ID}")
        time.sleep(1) # Give the registry a heartbeat to sync
    elif resp.status_code == 404:
        print(f"  ℹ️ UI Page {PAGE_ID} not found (already clean).")
    else:
        print(f"  ⚠️ UI Delete failed: {resp.status_code} - {resp.text}")

    # 2. DELETE LINKS
    try:
        links = request("GET", "links").json()
        for l in [l for l in links if BINDING_PREFIX in l['channelUID']]:
            request("DELETE", f"links/{l['itemName']}/{l['channelUID'].replace(':', '%3A')}")
    except: pass
    
    # 3. DELETE THINGS
    try:
        things = request("GET", "things").json()
        for t in [t for t in things if BINDING_PREFIX in t['UID']]:
            # force=true is key for stuck things
            request("DELETE", f"things/{t['UID']}?force=true")
    except: pass

    # 4. DELETE ITEMS
    try:
        items = request("GET", "items").json()
        prefix_filter = ("Zone_", "Area_", "Output_", "Elite_", "Ext_")
        for i in [i for i in items if i['name'].startswith(prefix_filter)]:
            request("DELETE", f"items/{i['name']}")
    except: pass

# --- 2. HELPERS ---
def create_thing(uid, type_id, bridge_uid, label, config):
    payload = {"UID": uid, "thingTypeUID": type_id, "bridgeUID": bridge_uid, "label": label, "configuration": config}
    request("POST", "things", payload)

def create_item_and_link(item_name, item_type, label, channel_uid, category):
    payload = {"type": item_type, "name": item_name, "label": label, "category": category}
    request("PUT", f"items/{item_name}", payload)
    link_queue.append((item_name, channel_uid))


# --- UPDATE UI PAGE FUNCTION ---
def create_ui_page():
    print(f"🖥️ Creating Dashboard (Compact Layout)...")

    # 1. Prepare System Health Card
    system_health_defs = [
        {"item": "Elite_Mains_Status", "title": "Mains", "icon": "energy"},
        {"item": "Elite_Battery_Status", "title": "Battery", "icon": "battery"},
        {"item": "Elite_Tamper_Status", "title": "Panel Tamper", "icon": "tamper"},
        {"item": "Elite_Fuse_Status", "title": "Panel Fuse", "icon": "error"},
        {"item": "Elite_Comms_Status", "title": "Comms", "icon": "network"},
    ]
    system_health_list_items = [
        {"component": "oh-label-item", "config": {"title": d["title"], "item": d["item"], "icon": f"oh:{d['icon']}", "iconUseState": True}} for d in system_health_defs
    ]
    system_health_card = {
        "component": "oh-list-card",
        "config": {"title": "System Health", "outline": True},
        "slots": {"default": system_health_list_items}
    }

    # 2. Prepare Zone Cards
    zone_card_wrappers = []
    for i in range(1, 16): # 15 zones for 3 rows
        zone_items_defs = [
            {"title": "Unsealed", "item": f"Zone_{i}_Status", "icon": "contact"},
            {"title": "Alarm", "item": f"Zone_{i}_Alarm", "icon": "alarm"},
            {"title": "Trouble", "item": f"Zone_{i}_Trouble", "icon": "error"},
            {"title": "Bypass", "item": f"Zone_{i}_Bypass", "icon": "lock"},
        ]
        list_items = [{"component": "oh-label-item", "config": {"title": zid["title"], "item": zid["item"], "icon": f"oh:{zid['icon']}", "iconUseState": True}} for zid in zone_items_defs]
        
        # Each card is wrapped in a grid column for the inner grid
        card_wrapper = {
            "component": "oh-grid-col",
            "config": {"width": "20", "tablet": "20", "desktop": "20"},
            "slots": {"default": [{
                "component": "oh-list-card",
                "config": {"title": f"Zone {i}", "outline": True},
                "slots": {"default": list_items}
            }]}
        }
        zone_card_wrappers.append(card_wrapper)

    # 3. Prepare Outputs Card
    output_list_items = [
        {"component": "oh-label-item", "config": {"title": f"Output {i}", "item": f"Output_{i}_State", "icon": "oh:switch", "iconUseState": True}} for i in range(1, 9)
    ]
    output_card = {
        "component": "oh-list-card",
        "config": {"title": "Outputs", "outline": True},
        "slots": {"default": output_list_items}
    }

    # 4. Assemble Page
    # Block 1: Health and Outputs
    health_and_outputs_block = {
        "component": "oh-block",
        "config": {}, # No title for this container
        "slots": {
            "default": [
                {
                    "component": "oh-grid-row",
                    "slots": {
                        "default": [
                            {"component": "oh-grid-col", "config": {"desktop": "33"}, "slots": {"default": [system_health_card]}},
                            {"component": "oh-grid-col", "config": {"desktop": "33"}, "slots": {"default": [output_card]}},
                        ]
                    }
                }
            ]
        }
    }

    # Block 2: Zones
    zones_block = {
        "component": "oh-block",
        "config": {"title": "Security Zones"},
        "slots": {
            "default": [
                {"component": "oh-grid-row", "slots": {"default": zone_card_wrappers}}
            ]
        }
    }

    page_def = {
        "uid": PAGE_ID,
        "component": "oh-layout-page",
        "config": {"label": "Elite Alarm Dashboard", "sidebar": True},
        "slots": {"default": [
            health_and_outputs_block,
            zones_block
        ]}
    }

    url = f"{OH_URL}/ui/components/ui:page"
    resp = requests.post(url, headers=HEADERS, json=page_def)
    
    if resp.status_code in [200, 201]:
        print(f"  ✅ Dashboard Created with compact layout.")
    else:
        print(f"  ❌ Error: {resp.status_code} - {resp.text}")

# --- 3. MAIN EXECUTION ---
def main():
    print("🚨 WARNING: This script will DELETE and CREATE things, items, and UI pages.")
    print("It is intended for use on a TEST openHAB instance only.")
    
    confirm = input("❓ Do you want to continue? (y/n): ")
    
    if confirm.lower() != 'y':
        print("🛑 Aborting.")
        return

    cleanup()
    time.sleep(2)

    print("🏗️ Creating Bridge & System...")
    bridge_uid = f"{BINDING_PREFIX}:bridge:panel"
    create_thing(bridge_uid, f"{BINDING_PREFIX}:bridge", None, "Elite Panel", {"host": "127.0.0.1", "port": 9000})

    sys_uid = f"{BINDING_PREFIX}:system:panel:stats"
    create_thing(sys_uid, f"{BINDING_PREFIX}:system", bridge_uid, "Panel Health", {})
    create_item_and_link("Elite_Mains_Status", "Switch", "Panel Mains Power", f"{sys_uid}:mains-trouble", "energy")
    create_item_and_link("Elite_Battery_Status", "Switch", "Panel Battery", f"{sys_uid}:battery-trouble", "battery")
    create_item_and_link("Elite_Tamper_Status", "Switch", "Panel Tamper", f"{sys_uid}:tamper-trouble", "tamper")
    create_item_and_link("Elite_Fuse_Status", "Switch", "Panel Fuse", f"{sys_uid}:fuse-trouble", "error")
    create_item_and_link("Elite_Expander_Status", "Switch", "Panel Expander Trouble", f"{sys_uid}:expander-trouble", "error")
    create_item_and_link("Elite_Receiver_Status", "Switch", "Panel Receiver Trouble", f"{sys_uid}:receiver-trouble", "receiver")
    create_item_and_link("Elite_Dialer_Status", "Switch", "Panel Dialer Trouble", f"{sys_uid}:dialer-trouble", "phone")
    create_item_and_link("Elite_Line_Status", "Switch", "Panel Line Trouble", f"{sys_uid}:line-trouble", "line")
    create_item_and_link("Elite_Comms_Status", "Switch", "Panel Comms Trouble", f"{sys_uid}:communication-trouble", "network")
    create_item_and_link("Elite_Panic_Alarm", "Switch", "Panel Panic Alarm", f"{sys_uid}:panic-alarm", "siren")
    create_item_and_link("Elite_Fire_Alarm", "Switch", "Panel Fire Alarm", f"{sys_uid}:fire-alarm", "fire")
    create_item_and_link("Elite_Medical_Alarm", "Switch", "Panel Medical Alarm", f"{sys_uid}:medical-alarm", "medical")
    create_item_and_link("Elite_Pendant_Bat", "Switch", "Panel Pendant Battery", f"{sys_uid}:pendant-battery-trouble", "battery")


    print("📦 Staging Things & Items...")
    for i in range(1, 33):
        t_uid = f"{BINDING_PREFIX}:zone:panel:z{i}"
        create_thing(t_uid, f"{BINDING_PREFIX}:zone", bridge_uid, f"Zone {i}", {"zoneNumber": i})
        create_item_and_link(f"Zone_{i}_Status", "Switch", f"Zone {i} Status", f"{t_uid}:unsealed", "door")
        create_item_and_link(f"Zone_{i}_Alarm", "Switch", f"Zone {i} Alarm", f"{t_uid}:alarm", "alarm")
        create_item_and_link(f"Zone_{i}_Status_Text", "String", f"Zone {i} Status Text", f"{t_uid}:status", "text")
        create_item_and_link(f"Zone_{i}_Trouble", "Switch", f"Zone {i} Trouble", f"{t_uid}:trouble", "error")
        create_item_and_link(f"Zone_{i}_Bypass", "Switch", f"Zone {i} Bypass", f"{t_uid}:bypass", "lock")
        create_item_and_link(f"Zone_{i}_Battery", "Switch", f"Zone {i} Battery", f"{t_uid}:battery", "battery")
        create_item_and_link(f"Zone_{i}_Supervise", "Switch", f"Zone {i} Supervise", f"{t_uid}:supervise", "eye")
        create_item_and_link(f"Zone_{i}_SensorWatch", "Switch", f"Zone {i} Sensor Watch", f"{t_uid}:sensor-watch", "eye")
        create_item_and_link(f"Zone_{i}_EntryDelay", "Switch", f"Zone {i} Entry Delay", f"{t_uid}:entry-delay", "time")

    for i in range(1, 9):
        t_uid = f"{BINDING_PREFIX}:area:panel:a{i}"
        create_thing(t_uid, f"{BINDING_PREFIX}:area", bridge_uid, f"Area {i}", {"areaNumber": i})
        create_item_and_link(f"Area_{i}_Status", "String", f"Area {i} Status", f"{t_uid}:area-status", "shield")
        create_item_and_link(f"Area_{i}_LastUser", "String", f"Area {i} Last User", f"{t_uid}:last-user", "person")
        create_item_and_link(f"Area_{i}_ArmedAway", "Switch", f"Area {i} Armed Away", f"{t_uid}:armed-away", "lock")
        create_item_and_link(f"Area_{i}_ArmedStay", "Switch", f"Area {i} Armed Stay", f"{t_uid}:armed-stay", "lock")
        create_item_and_link(f"Area_{i}_Alarm", "Switch", f"Area {i} Alarm", f"{t_uid}:alarm", "alarm")
        create_item_and_link(f"Area_{i}_Ready", "Switch", f"Area {i} Ready", f"{t_uid}:ready", "check")
        create_item_and_link(f"Area_{i}_ExitDelay", "Switch", f"Area {i} Exit Delay", f"{t_uid}:exit-delay", "time")

    for i in range(1, 9):
        t_uid = f"{BINDING_PREFIX}:output:panel:o{i}"
        create_thing(t_uid, f"{BINDING_PREFIX}:output", bridge_uid, f"Output {i}", {"outputNumber": i})
        create_item_and_link(f"Output_{i}_State", "Switch", f"Output {i} State", f"{t_uid}:state", "light")

    for etype in ["input_expander", "output_expander", "prox_expander"]:
        for i in range(1, 3):
            t_uid = f"{BINDING_PREFIX}:{etype}:panel:ext{i}"
            create_thing(t_uid, f"{etype.replace('_',' ')}", bridge_uid, f"{etype.title()} {i}", {"expanderNumber": i})
            create_item_and_link(f"Ext_{etype}_{i}_Mains", "Switch", f"Ext {i} Mains", f"{t_uid}:mains-trouble", "energy")
            create_item_and_link(f"Ext_{etype}_{i}_Battery", "Switch", f"Ext {i} Battery", f"{t_uid}:battery-trouble", "battery")
            create_item_and_link(f"Ext_{etype}_{i}_Tamper", "Switch", f"Ext {i} Tamper", f"{t_uid}:tamper-trouble", "tamper")
            create_item_and_link(f"Ext_{etype}_{i}_Fuse", "Switch", f"Ext {i} Fuse", f"{t_uid}:fuse-trouble", "error")

    print(f"⏳ Waiting 10s for Registry sync...")
    time.sleep(10)

    print(f"🔗 Linking {len(link_queue)} items...")
    for item, channel in link_queue:
        request("PUT", f"links/{item}/{channel.replace(':', '%3A')}", {"itemName": item, "channelUID": channel})

    # --- CALL THE UI CREATION FUNCTION ---
    create_ui_page()

    print("\n🚀 Environment Rebuilt & Dashboard Ready!")

if __name__ == "__main__":
    main()