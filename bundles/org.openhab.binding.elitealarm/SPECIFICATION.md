# openHAB EliteAlarm Binding: Specification and Design

This document details the design and implementation of the openHAB binding for the EliteAlarm security system.

## 1. Overview

The EliteAlarm binding integrates openHAB with EliteAlarm security panels, enabling users to monitor and control their alarm system from within the openHAB ecosystem. The binding communicates with the alarm panel over a TCP/IP network connection using a proprietary text-based protocol.

Key features include:

- Real-time status updates for zones, areas, and system health.
- Control of outputs and arming/disarming of areas.
- A bridge-and-thing architecture for representing the panel and its components.
- Automatic reconnection handling to ensure a stable connection.

## 2. Architecture

The binding is designed around a clear separation of concerns, with distinct components for handling openHAB integration, network communication, and protocol parsing.

### Main Components

- **`EliteAlarmHandlerFactory`**: An OSGi `ThingHandlerFactory` that is the entry point for the binding. It is responsible for creating the appropriate handlers (`EliteAlarmBridgeHandler` or `EliteAlarmThingHandler`) for the things defined in openHAB.

- **`EliteAlarmBridgeHandler`**: This is the handler for the `bridge` thing. It is the central coordinator of the binding. Its responsibilities include:
  - Managing the lifecycle of the connection to the alarm panel.
  - Initializing the `EliteAlarmNettyClient`.
  - Receiving parsed protocol messages from the `EliteAlarmHandler`.
  - Dispatching state updates to the appropriate child `EliteAlarmThingHandler`s.
  - Handling commands sent from openHAB channels and translating them into protocol commands.
  - Managing the online/offline status of child things.

- **`EliteAlarmThingHandler`**: This handler manages individual `thing` types like zones, areas, outputs, and the system itself. It is a lightweight handler that primarily forwards commands to the bridge and receives state updates from it. It also performs configuration validation for the thing it manages.

- **`EliteAlarmNettyClient`**: A TCP client built using the Netty framework. It handles the low-level details of network communication:
  - Establishing a socket connection to the alarm panel.
  - Managing the Netty pipeline, which includes encoders, decoders, and the main protocol handler.
  - Implementing a reconnection strategy if the connection is lost.
  - Providing a simple `sendCommand` method for the `EliteAlarmBridgeHandler` to use.

- **`EliteAlarmHandler`**: A Netty `SimpleChannelInboundHandler` that sits in the Netty pipeline. It is responsible for:
  - Handling the TCP connection lifecycle events (active, inactive).
  - Buffering incoming data and splitting it into newline-delimited protocol messages.
  - Managing the authentication state machine (waiting for user/password prompts).
  - Passing complete protocol lines to the `EliteAlarmProtocolRegistry` for parsing.
  - Implementing an idle timeout to detect a dead connection and trigger reconnection.

- **`EliteAlarmProtocolRegistry`**: A static registry that contains the definitions for all known protocol messages. It uses a series of regular expressions to match incoming text lines to a specific protocol event. When a match is found, it extracts the relevant data (e.g., zone number, area status) and creates a `ProtocolMatch` object, which is then passed up to the `EliteAlarmBridgeHandler`.

## 3. Connection Management

The binding ensures a resilient connection to the alarm panel.

### Initialization

- When the `EliteAlarmBridgeHandler` is initialized, it reads the host, port, and credential configuration.
- It creates an instance of `EliteAlarmNettyClient` and calls its `start()` method.
- The `EliteAlarmNettyClient` initializes a Netty `EventLoopGroup` and calls `connect()`.

### Connection Lifecycle & Authentication

- The `EliteAlarmNettyClient` attempts to connect to the panel.
- Upon a successful TCP connection, the `EliteAlarmHandler`'s `channelActive` method is called. The handler's state is set to `WAITING_AUTH`.
- The alarm panel may send a "Welcome" message directly or prompt for a username and password.
- The `EliteAlarmHandler` processes these prompts (identified by the `EliteAlarmProtocolRegistry`) and sends the configured credentials.
- Once authentication is successful (either via "Welcome" or a generic "OK"), the handler transitions to the `READY` state and notifies the `EliteAlarmBridgeHandler` by calling `onConnectionStateChanged(true)`.
- The `EliteAlarmBridgeHandler` then sends a series of commands (`VERSION`, `MODE`, `STATUS`) to synchronize the state of all things.

### Reconnection Logic

- If the initial connection fails, the `EliteAlarmNettyClient` schedules a retry after the configured `reconnectInterval`.
- If a connection is lost, the channel's `closeFuture` listener in `EliteAlarmNettyClient` is triggered. It notifies the bridge handler (`onConnectionStateChanged(false)`) and schedules a reconnection attempt.
- The `EliteAlarmHandler` also has a `READER_IDLE` timeout. If no data is received from the panel for a certain period, it assumes the connection is stale, closes it, and triggers the reconnection logic.

### Disconnection

- When the binding is stopped or the bridge thing is disposed, `EliteAlarmBridgeHandler.dispose()` is called.
- This calls `EliteAlarmNettyClient.stop()`, which gracefully shuts down the Netty `EventLoopGroup` and closes the channel, preventing further reconnection attempts.

## 4. Protocol Implementation

The protocol is text-based, with commands and responses terminated by a newline character.

### Message Framing and Parsing

- The `EliteAlarmHandler` receives raw data as a stream of bytes.
- A `StringDecoder` in the Netty pipeline converts this to a Java `String`.
- The `EliteAlarmHandler` appends the incoming string to an internal `StringBuilder`.
- It continuously scans the buffer for newline characters (`
`). When one is found, it extracts the complete line, trims it, and processes it.
- The processed line is passed to `EliteAlarmProtocolRegistry.findMatch()`.

### The Protocol Registry

- The `EliteAlarmProtocolRegistry` is the core of the protocol parsing logic. It is a static class initialized with a comprehensive list of known protocol messages.
- Each message is defined as a `Definition` record, containing:
  - A unique signature (e.g., "ZO" for Zone Open).
  - The subsystem it belongs to (e.g., "Zone", "Area").
  - A human-readable description.
  - A compiled `Pattern` (regex) to match the raw message.
  - The target openHAB `channelId`.
  - A `mapper` function (a `BiConsumer`) that knows how to extract data from the regex match groups into a `ProtocolMatch` object.
- This design makes it easy to add or modify protocol definitions without changing the core handler logic.

### State Synchronization

A robust synchronization process is critical to ensure openHAB's state matches the panel's state, especially after a reconnection. The panel's protocol is tricky: it sends an acknowledgment (`OK Status`) _before_ sending the actual status data, and it provides no message to signal when the status dump is complete.

To handle this, the binding implements a time-based **"synchronization window"**. This approach prevents the unnecessary "flickering" of item states (e.g., ON -> OFF -> ON) during a resync.

The process is as follows:

- **Start of Sync Window**: When a connection is established (`onConnectionStateChanged(true)`), the bridge handler:
  - Sets an internal flag `isSyncing = true`.
  - Clears a temporary set used to track `updatedChannelsInSync`.
  - Sends the `STATUS` command to the panel to request a full status dump.
  - **Starts a 5-second timer.**

- **Collecting Status Updates**: For the next 5 seconds, the handler is in a "listening" mode.
  - As status messages arrive from the panel, the handler updates the corresponding openHAB channels.
  - Each time a channel is updated, its ID is added to the `updatedChannelsInSync` set.
  - Crucially, no channels are reset during this period. An item that is already `ON` and receives another `ON` update will not flap.

- **End of Sync Window & Resetting Stale Channels**: When the 5-second timer expires:
  - A cleanup method (`endSyncProcess`) is executed.
  - It immediately sets `isSyncing = false` to return to normal operation.
  - It then calls `resetStaleChannels()`, which iterates through all of the bridge's child channels.
  - Any channel whose ID is **not** in the `updatedChannelsInSync` set is considered stale (as the panel did not report its status) and is reset to its default "normal" state (e.g., `OFF`).

This method ensures that item states change only when they are genuinely different from what the panel reports, providing a smooth and reliable user experience.

## 5. Thing and Channel Management

The binding reflects the state of the alarm panel in the openHAB thing model.

### Thing Status Updates

- The `EliteAlarmBridgeHandler` is the source of truth for the connection status.
- When `onConnectionStateChanged` is called, the bridge updates its own status.
- It then iterates through all its child things and calls the `updateStatus` method on their handlers, ensuring that all things share the same online/offline status as the bridge.

### Channel State Updates

- A protocol message arrives and is parsed into a `ProtocolMatch` object by the `EliteAlarmHandler` and `EliteAlarmProtocolRegistry`.
- The `EliteAlarmBridgeHandler` receives this object in its `onMessageReceived` method.
- A `switch` statement on the `match.subsystem` routes the match to a dedicated handler method (e.g., `handleZoneUpdate`, `handleAreaUpdate`).
- These methods use the `updateChildThing` helper, which finds the correct child thing based on its type and configuration (e.g., finding the `zone` thing where `zoneNumber` matches the number from the protocol message).
- Once the correct thing is found, `updateChildChannel` is called, which in turn calls the `updateState` method on the `EliteAlarmThingHandler` to update the openHAB channel state.

### Command Handling

- A user interacts with an openHAB item linked to a channel (e.g., toggling a switch for an output).
- This triggers the `handleCommand` method on the `EliteAlarmThingHandler`.
- The thing handler simply forwards the command and channel UID to the `EliteAlarmBridgeHandler`'s `handleCommand` method.
- The bridge handler identifies which thing and channel the command is for, translates the openHAB command (e.g., `OnOffType.ON`) into the corresponding protocol command (e.g., `ON1
`), and sends it using the `EliteAlarmNettyClient`.
