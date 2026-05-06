# EliteAlarm Binding

This binding integrates the EliteAlarm security system with openHAB.
It allows openHAB to monitor and control EliteAlarm panels by communicating with them over a TCP connection.
The binding supports monitoring zones, system status, and partitions (areas), as well as controlling outputs.

## Supported Things

This binding supports the following thing types:

### Bridge

- `bridge`: The Elite Alarm Control Panel bridge. This is the main bridge that communicates with the alarm panel. It can be configured to connect via TCP.

### Things

- `zone`: Represents a single security zone (e.g., a door sensor, motion detector). It provides channels for status, alarm, tamper, and other states.
- `system`: Provides channels for monitoring the overall panel health, such as mains power, battery, and various trouble conditions.
- `area`: Represents a partition or area of the alarm system. It allows for monitoring armed status (Away/Stay), alarms, and readiness.
- `output`: Represents a controllable output on the alarm panel.
- `input_expander`: Represents an input expander module, monitoring its health (mains, battery, tamper, fuse).
- `output_expander`: Represents an output expander module, monitoring its health.
- `prox_expander`: Represents a proximity reader expander, monitoring its health.

## Discovery

This binding does not support automatic discovery of EliteAlarm panels or their components. All things must be configured manually.

## Configuration

### Bridge Configuration

The `bridge` thing requires configuration to connect to the EliteAlarm panel.

**Network Connection:**

| Parameter  | Type     | Description                               | Default | Required |
| ---------- | -------- | ----------------------------------------- | ------- | -------- |
| `host`     | text     | IP address or hostname for TCP connection |         | Yes      |
| `port`     | integer  | TCP Port                                  | 9000    | No       |
| `username` | text     | Username for the connection               |         | No       |
| `password` | password | Password for the connection               |         | No       |

**Common Parameters:**

| Parameter           | Type    | Description                   | Default | Required |
| ------------------- | ------- | ----------------------------- | ------- | -------- |
| `refreshInterval`   | integer | Refresh Interval in seconds   | 60      | Yes      |
| `reconnectInterval` | integer | Reconnect Interval in seconds | 10      | Yes      |

### Thing Configuration

#### Zone (`zone`)

| Parameter    | Type    | Description         | Required |
| ------------ | ------- | ------------------- | -------- |
| `zoneNumber` | integer | Zone Number (1-248) | Yes      |

#### System (`system`)

This thing has no configuration parameters. It must be connected to the bridge.

#### Area (`area`)

| Parameter    | Type    | Description       | Required |
| ------------ | ------- | ----------------- | -------- |
| `areaNumber` | integer | Area Number (1-8) | Yes      |

#### Output (`output`)

| Parameter      | Type    | Description          | Required |
| -------------- | ------- | -------------------- | -------- |
| `outputNumber` | integer | Output Number (1-32) | Yes      |

#### Expanders (`input_expander`, `output_expander`, `prox_expander`)

| Parameter        | Type    | Description                                                   | Required |
| ---------------- | ------- | ------------------------------------------------------------- | -------- |
| `expanderNumber` | integer | The ID of the expander (Input: 1-30, Output: 1-8, Prox: 1-32) | Yes      |

## Channels

### Zone Channels

| Channel ID     | Type   | Description            | Read/Write |
| -------------- | ------ | ---------------------- | ---------- |
| `status`       | String | Zone Status Text       | R          |
| `unsealed`     | Switch | Unsealed status        | R          |
| `alarm`        | Switch | Alarm Status           | R          |
| `tamper`       | Switch | Tamper Status          | R          |
| `bypass`       | Switch | Bypass Status          | R          |
| `trouble`      | Switch | Trouble Status         | R          |
| `battery`      | Switch | Battery Status         | R          |
| `supervise`    | Switch | Supervise Status       | R          |
| `sensor-watch` | Switch | Sensor Watch Status    | R          |
| `entry-delay`  | String | Entry Delay in seconds | R          |

### System Channels

| Channel ID                | Type   | Description               | Read/Write |
| ------------------------- | ------ | ------------------------- | ---------- |
| `mains-trouble`           | Switch | Panel Mains Failure       | R          |
| `battery-trouble`         | Switch | Panel Battery Trouble     | R          |
| `tamper-trouble`          | Switch | Panel Tamper              | R          |
| `fuse-trouble`            | Switch | Panel Fuse/Output Failure | R          |
| `receiver-trouble`        | Switch | Wireless Receiver Fault   | R          |
| `dialer-trouble`          | Switch | Dialer Failure            | R          |
| `line-trouble`            | Switch | Line Failure              | R          |
| `communication-trouble`   | Switch | Communication Active      | R          |
| `panic-alarm`             | Switch | Panic Alarm               | R          |
| `fire-alarm`              | Switch | Fire Alarm                | R          |
| `medical-alarm`           | Switch | Medical Alarm             | R          |
| `pendant-battery-trouble` | Switch | Pendant Battery Trouble   | R          |

### Area Channels

| Channel ID    | Type   | Description       | Read/Write |
| ------------- | ------ | ----------------- | ---------- |
| `armed-away`  | Switch | Armed Away        | R          |
| `armed-stay`  | Switch | Armed Stay        | R          |
| `alarm`       | Switch | Area Alarm        | R          |
| `ready`       | Switch | Ready to Arm      | R          |
| `area-status` | String | Area Status Text  | R          |
| `last-user`   | String | Last User         | R          |
| `exit-delay`  | Switch | Exit Delay Active | R          |

### Output Channels

| Channel ID | Type   | Description  | Read/Write |
| ---------- | ------ | ------------ | ---------- |
| `state`    | Switch | Output State | RW         |

## Full Example

Here is an example of how to configure the EliteAlarm binding in your `.things` and `.items` files.

### `elitealarm.things`

```java
bridge elitealarm:bridge:panel "Elite Alarm Panel" [ host="192.168.1.100", port=9000, refreshInterval=30 ] {
    Thing zone 1 "Front Door" [ zoneNumber=1 ]
    Thing zone 2 "Living Room PIR" [ zoneNumber=2 ]
    Thing area 1 "House" [ areaNumber=1 ]
    Thing system system "Panel Health"
    Thing output 1 "Garage Door" [ outputNumber=1 ]
}
```

### `elitealarm.items`

```java
// Zone Items
String  FrontDoor_Status      "Front Door Status [%s]"      { channel="elitealarm:zone:panel:1:status" }
Switch  FrontDoor_Unsealed    "Front Door"                  { channel="elitealarm:zone:panel:1:unsealed" }
Switch  LivingRoomPIR_Alarm   "Living Room PIR Alarm"       { channel="elitealarm:zone:panel:2:alarm" }

// Area Items
Switch  House_ArmedAway       "House Armed Away"            { channel="elitealarm:area:panel:1:armed-away" }
Switch  House_ArmedStay       "House Armed Stay"            { channel="elitealarm:area:panel:1:armed-stay" }
String  House_AreaStatus      "House Status [%s]"           { channel="elitealarm:area:panel:1:area-status" }
String  House_LastUser        "Last User [%s]"              { channel="elitealarm:area:panel:1:last-user" }

// System Items
Switch  Panel_MainsFailure    "Panel Mains Failure"         { channel="elitealarm:system:panel:system:mains-trouble" }
Switch  Panel_BatteryTrouble  "Panel Battery Trouble"       { channel="elitealarm:system:panel:system:battery-trouble" }

// Output Items
Switch  GarageDoor_Control    "Garage Door"                 { channel="elitealarm:output:panel:1:state" }
```

## Tools and testing

See [README_TESTING.md](dev-resources/test_harness/README_TESTING.md) for details on testing harness and test environment configuration tools
