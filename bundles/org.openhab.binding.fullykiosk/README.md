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

## Thing Properties

When the device is online, the Thing shows its vendor, model, MAC address, Fully Kiosk device ID, device name and Fully Kiosk version as properties.

## Channels

| Channel             | Type               | Read/Write | Advanced | Description                                             |
|---------------------|--------------------|------------|----------|---------------------------------------------------------|
| screen              | Switch             | RW         | no       | Turns the screen on or off                              |
| screensaver         | Switch             | RW         | no       | Starts or stops the Fully Kiosk screensaver             |
| screen-brightness   | Dimmer             | RW         | no       | Brightness of the screen                                |
| url                 | String             | RW         | no       | Currently displayed page; send a URL to load it         |
| action              | String             | W          | no       | Triggers a one-time action, see below                   |
| battery-level       | Number             | R          | no       | Battery charge level (0-100 %)                          |
| battery-temperature | Number:Temperature | R          | no       | Temperature of the device battery                       |
| plugged             | Switch             | R          | no       | Whether the device is connected to a power source       |
| foreground-app      | String             | R          | yes      | Package name of the app currently in the foreground     |
| screen-orientation  | Number             | R          | yes      | Current screen orientation as reported by the device    |
| kiosk-mode          | Switch             | R          | yes      | Whether kiosk mode is active                            |
| device-admin        | Switch             | R          | yes      | Whether Fully Kiosk has device administrator permission |
| storage-free        | Number:DataAmount  | R          | yes      | Free internal storage                                   |
| storage-total       | Number:DataAmount  | R          | yes      | Total internal storage                                  |
| memory-free         | Number:DataAmount  | R          | yes      | Free RAM                                                |
| memory-total        | Number:DataAmount  | R          | yes      | Total RAM                                               |

The `screen-brightness` channel reads the Fully Kiosk settings, which are only requested while the channel is linked.

The `action` channel accepts these commands:

| Command       | Description                               |
|---------------|-------------------------------------------|
| loadStartUrl  | Loads the start URL                       |
| toForeground  | Brings Fully Kiosk to the foreground      |
| toBackground  | Sends Fully Kiosk to the background       |
| restartApp    | Restarts Fully Kiosk                      |
| clearCache    | Clears the browser cache                  |
| triggerMotion | Simulates motion, e.g. to wake the screen |

## Full Example

### Thing Configuration

```java
Thing fullykiosk:device:hallway "Hallway Tablet" [ hostname="192.168.1.50", password="secret" ]
```

### Item Configuration

```java
Switch HallwayTablet_Screen "Hallway Tablet Screen" { channel="fullykiosk:device:hallway:screen" }
Dimmer HallwayTablet_Brightness "Hallway Tablet Brightness [%d %%]" { channel="fullykiosk:device:hallway:screen-brightness" }
String HallwayTablet_Url "Hallway Tablet Page [%s]" { channel="fullykiosk:device:hallway:url" }
Number HallwayTablet_Battery "Hallway Tablet Battery [%d %%]" { channel="fullykiosk:device:hallway:battery-level" }
```
