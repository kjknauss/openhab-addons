# Fully Kiosk Browser Binding

This binding integrates Android devices running [Fully Kiosk Browser](https://www.fully-kiosk.com/), such as wall-mounted tablets.
It communicates with the device through the Fully Kiosk Remote Admin REST interface.

## Prerequisites

- Remote Admin requires a Fully Kiosk PLUS license on the device.
- In the Fully Kiosk settings, enable Remote Admin and set a Remote Admin password.

## Supported Things

- `device`: An Android device running Fully Kiosk Browser.

## Discovery

Discovery is not supported.
Things must be added manually.

## Thing Configuration

### `device` Thing Configuration

| Name            | Type    | Description                                                    | Default | Required | Advanced |
|-----------------|---------|----------------------------------------------------------------|---------|----------|----------|
| hostname        | text    | Hostname or IP address of the device                           | N/A     | yes      | no       |
| port            | integer | Port of the Fully Kiosk Remote Admin interface                 | 2323    | no       | yes      |
| password        | text    | Password set in the Fully Kiosk Remote Administration settings | N/A     | yes      | no       |
| refreshInterval | integer | Interval in seconds between polls of the device                | 30      | no       | yes      |

## Channels

| Channel       | Type   | Read/Write | Description                    |
|---------------|--------|------------|--------------------------------|
| battery-level | Number | R          | Battery charge level (0-100 %) |

## Full Example

### Thing Configuration

```java
Thing fullykiosk:device:hallway "Hallway Tablet" [ hostname="192.168.1.50", password="secret" ]
```

### Item Configuration

```java
Number HallwayTablet_Battery "Hallway Tablet Battery [%d %%]" { channel="fullykiosk:device:hallway:battery-level" }
```
