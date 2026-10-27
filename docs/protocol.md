# SRAM AXS / Quarq Bluetooth protocol

What Cassette knows about how AXS components and Quarq power meters talk over Bluetooth LE, written in our
own words. This is **unofficial**. It is not SRAM documentation and may be wrong for groupsets we haven't
seen.

## 1. Scope and sources

Validated on **one** Red AXS bike: rear derailleur (model 1066), front derailleur (1002), right and left
drop-bar shifters (1004 / 1005) and a Quarq DZero power meter. A tester's mountain bike adds adverts from a
rear derailleur (1013), a Reverb AXS dropper (1019), a fork and a rear shock (1086 / 1085), an older AXS
controller (1016), a new-generation pod (1073) and another DZero, plus the dropper's hub table.
Eagle Transmission, Powertrain and blips are covered only by prior art or by what SRAM's app expects.

| Source | What it gave us |
|---|---|
| Our decoder and its tests (`axs/AxsAdvertParser.kt`, `axs/SystemBatteryTable.kt`, `scan/BatteryGattReader.kt`, tests under `app/src/test/`) | The authoritative layouts. Every hex example in this doc comes from the tests. |
| HCI snoop of the official app talking to our bike | GATT order, which characteristics are plaintext, the hub tables |
| Read-only analysis of SRAM's Android app (v2.54.0) | Field layouts, enums, the model table, battery thresholds, UUID purposes |
| [tarekrached/esphome-sram-axs](https://github.com/tarekrached/esphome-sram-axs) `docs/protocol.md` | The first public advert format, wake and advertising windows, the app silencing adverts, status-frame samples |
| [karl-petter/sram-axs-for-ha](https://github.com/karl-petter/sram-axs-for-ha) | Some AXS controllers expose the standard Battery Service (0x180F) |
| [ShannoG/home-assistant-sram-axs](https://github.com/ShannoG/home-assistant-sram-axs) `advertised-data/` | A Transmission RD (1060) and a Reverb AXS dropper (1025) that both flag themselves as the bridge |
| A tester's diagnostics export (mountain bike, 2026-10-26: capture log and database, about 25 h) | Mountain components' frames, a dropper as hub, two hubs on one bike, more pack (mV, %) pairs |
| The same tester's own passive-listening app: its notes, logs and source (shared 2026-10-27) | Only the Quarq and the new-generation pod need a Battery Level read. Also the reported status's hysteresis, a full pack discharge, the pod's GATT reads and an 8-byte frame without a device type |
| [GaborWnuk/sram-axs](https://github.com/GaborWnuk/sram-axs) `PROTOCOL.md` | A GX Eagle Transmission RD (1075, which SRAM's app calls `RD-GX-E-B1`) and an AXS Controller (1016). It gave us `fe5x` reads and a bench test of `d9050003` that confirms two hub-table fields. It also documents the authenticated session, which Cassette doesn't use |

Confidence tags used on every claim:

| Tag | Meaning |
|---|---|
| **[HW]** | Confirmed on hardware: our bike and captures, or the tester's Cassette capture where the text says so |
| **[APP]** | Confirmed in SRAM's app (its parsing or UI logic), not necessarily seen on our bike |
| **[PA]** | Reported by prior art; we haven't re-checked it |
| **[INF]** | Inferred from structure, names or a few samples |
| **[?]** | Unknown |

All multi-byte integers are **little-endian** unless stated otherwise. Custom 128-bit UUIDs share the base
`d905xxxx-90aa-4c7c-b036-1e01fb8eb7ee`. This doc writes them as their short form (`d9050003`).

## 2. Wake and advertising behaviour

| Behaviour | Detail | Conf. |
|---|---|---|
| Components advertise only while awake | Nothing is heard from a sleeping bike | [HW] [PA] |
| Wake triggers | Motion ("shake the bike") wakes the rear derailleur; an AXS button press wakes controllers and droppers. Shift-paddle clicks reportedly don't wake the radio | [HW] motion, [PA] buttons |
| Battery insertion | Produces no advertisement | [PA] |
| Missed presses | A button press occasionally produces no advert even on fresh cells; don't treat one quiet press as a flat battery | [PA] |
| Pack components (RD, dropper) | Advertise several times a second for about 5 min 20 s after waking (measured 318 s and 326 s). One RD kept going for 10+ min after shifts, so activity probably extends the window | [PA]; ~5 min sleep after motion stops [HW] |
| Coin-cell controllers | About 20–24 s after a button press | [PA] |
| Our shifters and FD | Send short beacon frames (no battery) for roughly half a minute after the bike wakes | [HW] |
| During a ride | Two 13–15 min pocket rides: the longest gap between background wakes was 46 s | [HW] |
| Quarq | Advertises a lot while riding: about 70% of Cassette's background scan wakes on our rides. It wakes when the crank turns, sleeps about 10 min after it stops, and accepts connections only while awake | [HW]; sleep and connections [PA] (tester) |
| Official app connected | Components stop advertising (or stop sending scan responses and service data) while SRAM's app is connected. One capture showed pack devices going quiet in ~50 s gaps, and a coin controller still sending bare manufacturer data | [PA] |
| Hub / system bridge | The hub flags itself as its system's bridge in its service frame and holds battery and firmware tables for every component paired to it. On our bike it's the RD | [HW] [APP] |
| Who can be the hub | Any component with an AXS pack. Seen: RD (ours, ShannoG's), dropper (the tester's, ShannoG's), rear shock (the tester's) | [APP] [HW] [PA] |
| Two hubs on one bike | The tester's MTB is two systems: the dropper and the rear shock both flag the bridge, and the dropper's table lists only fork, pod and dropper. The RD and the older controller are in no table we read, so most likely the shock's. ShannoG's RD and dropper both flag it too, heard by one receiver 5 s apart | [HW] (tester), [PA]; the shock's table [?] |
| Pairing | Receiving adverts needs no pairing, bonding or app | [HW] |

## 3. Advertisements

### 3.1 Manufacturer data, company `0x0933`

The payload after the company id is a list of records:

```
[type u16][len u8][payload: len bytes]   repeated; a trailing partial record is ignored
```

| Type | Len | Payload | Conf. |
|---|---|---|---|
| `0x0000` | 1 | One byte, meaning unknown. `02` on our RD. Prior art saw `01` before a firmware update and `02` after | [HW] value, [?] meaning |
| `0x0400` | 5 | Battery: `mV u16`, `status u8` (soc, see §6), `0x80` (constant, unknown), `percent u8` (`0xFF` = not supported, which is what coin cells send) | [HW] [PA]; SRAM's app ignores this record [APP] |
| `0x0101` | 9 | A copy of the `0xFE51` service frame (§3.2) | [HW] [APP]. It's the only record SRAM's app decodes |
| `0x0402` | 10 | Firmware version as 3 bytes `major minor patch`, then a 7-char ASCII build tag | [HW] version, [INF] tag |

Examples from our captures and tests:

```
00 00 01 02 | 00 04 05 cb 1e 03 80 51          RD: rec 0x0000=02; battery 0x1ecb=7883 mV, status 3 (OK), 0x80, 81 %
01 01 09 0d 03 9f fc 91 3c 32 04 81 | 01        rec 0x0101 = service frame, then a stray trailing byte 01
02 04 0a 02 37 08 67 39 39 66 66 66 34 | 02     rec 0x0402 = 2.55.8 + "g99fff4", then a trailing byte 02
00 00 01 02 | 00 04 05 b6 1f 03 80 64          esphome sample: 8118 mV, OK, 100 %
```

- Our RD sends the battery records in one AD structure and **rotates** the second 0x0933 structure between
  the `0x0101` and `0x0402` forms. [HW] The trailing `01` / `02` byte might be a rotation index. [INF]
- The build tag is the prefix of the `rev` string in the RD's `fe58` characteristic (`g99fff42034.dir`). [HW]
- On the tester's MTB the serial-less frames carry `0x0402`: RD 2.54.2, fork and shock 2.55.11, dropper
  2.55.8 (the same `g99fff4` tag as our RD), older controller 2.55.1. With the voltage, that's how we
  matched each serial-less address to its component. [HW]
- Percent tracks voltage from frame to frame (`c8 1e` → 80 %, `cb 1e` → 81 %). [HW]
- Prior art read bytes 2–3 (`01 01` / `01 02`) as a firmware version. With the record framing they are the
  length and value of record `0x0000`. [INF]

**Android pitfall:** an advert can carry **two** `0xFF` AD structures with the same company id.
`ScanRecord.getManufacturerSpecificData()` keeps only the last one. Walk the raw `ScanRecord.bytes`, stop
at the first zero length byte (Android pads with zeros) and join every 0x0933 payload before splitting the
records. CoreBluetooth merges them (confirmed on our RD: one blob holds `0000,0400,0101`); ESP32 stacks
deliver them separately. [HW] [PA]

**iOS:** callbacks that carry the scan response (name and `FE51` service data) hold only the `0000,0400`
records. In the background, iOS delivers no scan response at all: the battery records arrive, but not
the serial. [HW] (iOS spike, decision 0017)

### 3.2 Service data `0xFE51`: the SRAM service frame

A flags-driven frame. Each field is present only if its flag is set, and fields always appear in this
order. [APP] (layout) [HW] (frames below)

| Order | Flag | Field | Notes |
|---|---|---|---|
| 1 | — | `flags u8` | |
| 2 | flags bit0 `0x01` | `flags2 u8` | |
| 3 | flags bit1 `0x02` | 7 bytes, skipped | Only seen on Quarq [HW] |
| 4 | flags bit2 `0x04` | `serial u32` | **Required**; reject the frame without it |
| 5 | flags bit3 `0x08` | `model id u16` | See §4.2 |
| — | flags bit4 `0x10` | (no payload) | Device is in DFU (firmware update) mode [APP] |
| 6 | flags bit5 `0x20` | `quarq model code u16` | model id = code `& 0x3F` [APP] [HW] |
| 7 | flags bit6 `0x40` | firmware, 3 bytes | `b0.b1.b2`; with bit5 set, only `b2` as one number |
| 8 | flags bit7 `0x80` | `model flags u32` | Meaning [?] |
| 9 | flags2 bit1 `0x02` | `device type u8` | See §4.1 |
| — | flags2 bit0 `0x01` | (no payload seen) | The component is the system **bridge** (hub) |

Post-processing in SRAM's app: for model ids 7, 8 and 9 (DZero, ShockWiz) the serial's 16-bit halves are
swapped. [APP]

### 3.3 Frame shapes per component

| Frame | Who | Contents | Example service data (after `51 fe`) | Conf. |
|---|---|---|---|---|
| **Status** | Our RD; on the tester's MTB every pack component and the older AXS controller (a coin cell, percent `0xFF`) | Manufacturer records with `0x0400` battery + service frame with device type, and the bridge flag on hubs | `0d 03 6e 7a c1 53 2a 04 81`: flags `0x0d`, flags2 `0x03` (bridge + type), serial 0x53c17a6e = 1405188718, model 0x042a = 1066, type `0x81` RD | [HW] |
| | | | `0d 03 ec d7 d2 42 fb 03 84`: bridge + type, serial 1121114092, model 1019, type `0x84` dropper (a hub) | [HW] (tester) |
| | | | `0d 03 64 df 69 3c 3d 04 83`: bridge + type, serial 1013571428, model 1085, type `0x83` rear shock (a hub) | [HW] (tester) |
| | | | `0d 02 e2 77 a2 44 3e 04 82`: type only, serial 1151498210, model 1086, type `0x82` fork | [HW] (tester) |
| | | | `0d 02 3f 55 f7 48 f5 03 81`: type only, serial 1224168767, model 1013, type `0x81` RD, **not** a bridge | [HW] (tester) |
| | | | `0d 02 fd 22 f7 48 f8 03 04`: type only, serial 1224155901, model 1016, type 4 (older AXS controller) | [HW] (tester) |
| Status, serial-less | Our RD; every status sender on the tester's MTB | Manufacturer battery records only (plus `0x0402` firmware), no service data or name. Match them to a component by BLE address | — | [HW] |
| **Beacon** | Our shifters and FD | Flags `02 01 06`, 16-bit UUID list `03 02 51 fe`, service frame with serial, model and firmware, **no battery and no device type** | `4c f2 fe 7a 43 ec 03 02 07 03`: flags `0x4c`, serial 1132134130, model 1004 (right shifter), fw 2.7.3 | [HW] |
| | | | `4c 31 2e 7b 43 ed 03 02 07 03`: model 1005 (left shifter) | [HW] |
| | | | `4c cb 66 63 4e ea 03 02 07 03`: serial 1315137227, model 1002 (FD) | [HW] |
| Beacon with type | New-generation pod | As above, but **with** a device type byte. No battery | `4d 02 7b a4 3e 50 31 04 02 25 01 73`: flags `0x4d`, flags2 `0x02`, serial 1346282619, model 1073 (pod ultimate), fw 2.37.1, type 115 | [HW] (tester) |
| **Quarq** | DZero | Appearance `0x0484` (cycling power sensor), UUIDs `0x1818` + `0xFE51`, name `Quarq <id>`, 23-byte service frame | `e6 00 00 00 00 00 00 00 63 30 cd 00 07 95 65 0b 0a 12 00 00 00`: flags `0xe6`, 7 skipped bytes, serial u32 `63 30 cd 00`, quarq code 0x9507 (& 0x3F = 7, DZero), version bytes `65 0b 0a` (version = 10), model flags 0x00000012 | [HW] |

Prior-art status samples: model 1074 RD `0d 03 9f fc 91 3c 32 04 81` (serial 1016200351); ShannoG's
model 1060 RD `0d 03 20 66 79 4f 24 04 81` and model 1025 dropper `0d 03 7a 45 a5 53 01 04 84`, both
bridges. [PA]

The tester's DZero sends `e6 00 00 00 00 00 00 00 e3 ed c3 00 07 55 65 0b 0a ff 00 00 00`: the same
shape as ours, quarq code 0x5507 (& 0x3F = 7), model flags 0x000000ff. [HW] (tester)

The tester's own app also logged an 8-byte frame from their fork: `0d 00 e2 77 a2 44 00 82`. By its flags
that's flags2 `0x00` (no device type, no bridge), serial 1151498210, and a model of 0x8200 = 33280,
which isn't a model id. It looks more like the fork's type (`0x82`) sitting where the model goes. The
frame never appeared in the tester's 25 h Cassette capture. Until we know what it is, a model id from a
frame without a device type shouldn't replace one we already have. [PA] (tester), [?] meaning

### 3.4 Local names

| Pattern | Meaning | Conf. |
|---|---|---|
| `SRAM <decimal serial>` | e.g. `SRAM 1132134130` = serial 0x437afef2, the same u32 as in the service frame | [HW] |
| `Quarq <id>` | e.g. `Quarq AGN12387`. Quarq ids are 3 letters + 5 digits, not the decimal SRAM serial | [HW] [APP] |

## 4. Device types

### 4.1 Device type byte

Carried in status frames (service frame `flags2` bit1) and in every hub-table row. SRAM's app takes left/right
**only** from this byte. [APP] Our bike: `0x81` RD, `0x80` FD, `0x01` right shifter, `0x00` left
shifter. [HW]

| Value | Component | Value | Component |
|---|---|---|---|
| 0 | Drop-bar shifter, left | 114–125 | AXS pod: **even = left, odd = right** |
| 1 | Drop-bar shifter, right | 127 | Host (app-side) |
| 2 | BlipBox | 128 `0x80` | Front derailleur |
| 3 / 4 | Reverb or AXS controller L / R | 129 `0x81` | Rear derailleur |
| 5 | Pedal sensor (Flight Attendant) | 130 `0x82` | Fork / Flight Attendant control |
| 6 | AirWiz | 131 `0x83` | Rear shock |
| 7 / 8 | Flat-bar 3-button controller L / R | 132 `0x84` | Dropper post (Reverb AXS) |
| 33–35 | Drop-bar / BlipBox / flat-bar combos | 133 `0x85` | Quarq DUB spindle power meter |
| 36–44 | Wireless Blip pairs | 134, 137, 138, 139 | Powertrain display, battery, drive unit, range extender |
| 96–113 | Wireless Blips 1–18 | 142 | Light |
| | | 255 | Invalid |

Everything is [APP]; 0, 1, 0x80, 0x81 are also [HW], and 4, 115, 0x82, 0x83 and 0x84 are [HW] on the
tester's MTB. Cassette doesn't map 33–44, 127, 142.

- **Types 3 / 4 aren't only Reverb.** SRAM's enum names them `REVERB_SHIFTER_L` / `_R`, but its
  `devices.yml` calls model 1015 "AXS CONTROLLER (R)", and the tester's older AXS controller (1016,
  on the right) reports type 4. Prior art names 1016 "AXS Controller" too. Cassette's "Reverb
  controller" label is too narrow for it. The tester's left Reverb controller reported type 3 before
  they replaced it. [APP] [HW] [PA]
- **Pod edge case** (`isPodEdgeCase` in SRAM's app): every pod is left when the pod's "family" has
  exactly two device types and one is a dropper (`POST`) or a Powertrain display, or exactly three and
  both are there. We didn't trace what the family is. If it's the system's device types, the tester's
  dropper system (fork, pod, dropper: three, no display) isn't the edge case, and its type-115 pod
  reads as right, as Cassette shows it. [APP] rule, [?] family
- **But the tester's pod may be on the left.** It replaced their left Reverb controller on 2026-10-24
  (that controller, 1020156614, isn't in the 25 h Cassette capture), and it's the only controller in
  the dropper's table. The rule above sits inside `decidePodLocation`, which returns a set of pod
  types, not a label, and we didn't find its callers. It may choose the type a pod gets when it's set
  up, rather than label one. Asking the tester which side the pod is on, and what SRAM's app calls it,
  would settle it. [HW] [PA] (tester), [?] side

### 4.2 Model id → type

Used when a frame has no device-type byte (beacons). [APP] Our bike: 1066 RD, 1002 FD, 1004 right shifter,
1005 left shifter. [HW] The tester's MTB: 1013 RD, 1016 controller, 1019 dropper. [HW] Prior art: 1075
is a GX Eagle Transmission RD, and 1016 an AXS Controller, both checked against SRAM's app. [PA] (GaborWnuk)

This is SRAM's table (`DeviceUtils::guess_device_type` in app 2.54.0). It covers 1000–1113, and any id it
leaves out is invalid to it, including ids now seen on hardware. SRAM's app names those from the type byte,
and so does Cassette, so the gaps only matter for a frame without one (the Red shifters' and FD's beacons;
the pod's beacon has one). [APP] [HW]

| Type | Model ids |
|---|---|
| Rear derailleur | 1000, 1001, 1013, 1014, 1033, 1036, 1056, 1066, 1070, 1071, 1074, 1075, 1081, 1082 |
| Front derailleur | 1002, 1003, 1037, 1045, 1051 |
| Drop-bar shifter, right | 1004, 1006, 1008, 1010, 1035, 1047, 1083, 1091, 1093, 1095, 1097, 1099, 1113 |
| Drop-bar shifter, left | 1005, 1007, 1009, 1011, 1034, 1046, 1084, 1090, 1092, 1094, 1096, 1098, 1112 |
| BlipBox | 1012 |
| Reverb controller, right / left | 1015, 1016 / 1017, 1044 |
| Dropper post | 1018–1029, 1067–1069 |
| Fork / rear shock / pedal sensor | 1038, 1048, 1049 / 1039 / 1040 |
| Power meter | 7 (DZero), 70–75 (Quarq DZero variants), 100–110 (PowerTap), 1052 (DUB spindle) |
| Not mapped by Cassette | 8, 9 (ShockWiz), 1030, 1031, 1119, 1125 (TyreWiz family); pods 1072, 1073, 1077, 1078 (identified by type byte only) |
| Seen, not in SRAM's table | 1085 rear shock and 1086 fork (the tester's, by type byte) [HW]; 1060 Transmission RD (ShannoG's, by type byte) [PA] |
| Named in SRAM's `SramModelId` enum, not in its table | 1058–1060, 1132, 1133 (Eagle Transmission RDs), 1138, 1139 (Force / Rival 1x RDs); also `APOLLO_HMI` 1076, three `SPACEFORCE_*_DISPLAY` ids (1185, 1194, 1204) and two `TOPGUN_*` kits (1198, 1199) [APP] |

Model **names** (e.g. "RED AXS E1 RD") aren't in the app. They come from SRAM's cloud. [APP] Cassette has no
network access, so names stay out of reach, except where someone recorded what SRAM's app showed: prior
art saw 1075 shown as `RD-GX-E-B1`. [PA] A newer app's enum may name more ids, but the kind already comes
from the type byte.

## 5. GATT

Cassette only does short, read-only sessions: connect, discover, read, disconnect. No pairing, no writes,
no subscriptions. [HW] Components drop an idle connection quickly anyway: within about 10–100 s on a GX
Transmission RD. [PA] (GaborWnuk)

### 5.1 Hub tables (plaintext)

Both tables are readable unpaired, without the authenticated session (§5.4). The official app also reads them
before starting that session. [HW] Cassette reads `d9050003` at most every 60 s while the app is open and every
10 min in the background, and reads `d9050004` only while the app is open. [HW]

Common header (6 bytes):

| Offset | Field | Notes |
|---|---|---|
| 0 | table tag | `0x01` battery, `0x02` firmware. Cassette uses it to reject the wrong table [HW]; SRAM's decoder doesn't check it [APP] |
| 1 | row count | |
| 2–5 | `masterSerial u32` | The hub's serial, i.e. the bike/system id |

**`d9050003`: battery / status table**, 24-byte rows [HW] [APP]

| Offset | Field | Notes | Conf. |
|---|---|---|---|
| +0 | device type u8 | §4.1 | [HW] |
| +1 | model id u16 | §4.2 | [HW] |
| +3 | serial u32 | | [HW] |
| +7 | soc s8 | Reported status, §6. SRAM's app clamps negatives to 0 | [HW] [APP] |
| +8 | millivolts u32 | | [HW] |
| +12 | update time u32 | 0 on every row we've seen | [APP] name, [?] meaning |
| +16 | "runtime" u32 | SRAM calls it runtime/operating time. Seconds awake on the tester's dropper and on prior art's GX RD (below). Our RD's value (0xf3ed42d2) changes between reads but is implausible as a duration | [APP] name, [PA] [INF] meaning |
| +20 | actuation count u32 | RD 12324. Counts shifts: +22 over 22 shifts on prior art's GX RD (below). The FD shows exactly **2000** (`d0 07 00 00`), which may be a placeholder | [APP] name, [PA] |

Real read (4 rows: FD, right shifter, left shifter, RD):

```
01 04 6e 7a c1 53                                         tag 1, 4 rows, master 1405188718
80 ea 03 cb 66 63 4e 03 97 1f 00 00 00 00 00 00 00 00 00 00 d0 07 00 00   FD 1002, OK, 8087 mV, act 2000
01 ec 03 f2 fe 7a 43 03 78 0b 00 00 00 00 00 00 00 00 00 00 00 00 00 00   R shifter 1004, OK, 2936 mV
00 ed 03 31 2e 7b 43 03 82 0b 00 00 00 00 00 00 00 00 00 00 00 00 00 00   L shifter 1005, OK, 2946 mV
81 2a 04 6e 7a c1 53 03 cc 1e 00 00 00 00 00 00 d2 42 ed f3 24 30 00 00   RD 1066, OK, 7884 mV, act 12324
```

Because the table covers the FD and shifters, Cassette never connects to them. [HW]

The tester's dropper, a hub, holds its system's three rows [HW] (tester):

```
01 03 ec d7 d2 42                                         tag 1, 3 rows, master 1121114092 (the dropper)
82 3e 04 e2 77 a2 44 03 28 1e 00 00 00 00 00 00 00 00 00 00 00 00 00 00   fork 1086, OK, 7720 mV
73 31 04 7b a4 3e 50 03 8d 0a 00 00 00 00 00 00 00 00 00 00 00 00 00 00   pod 1073 (type 115), OK, 2701 mV
84 fb 03 ec d7 d2 42 03 9b 1d 00 00 00 00 00 00 a5 1c 39 00 3d 15 00 00   dropper 1019, OK, 7579 mV, act 5437
```

Across six reads over a day, the dropper's actuation count climbed from 5437 to 5455, so the count is real
there. Its "runtime" (0x00391ca5 here) climbed by 306 to 1755 between reads, about 300–550 per wake we heard
in between. That's close to the ~320 s a pack stays awake, so it looks like seconds awake rather than a
clock. [HW] (tester), [INF] meaning

Prior art backs both fields. GaborWnuk polled `d9050003` once a second on a GX Transmission RD paired with
an AXS Controller and called it a 54-byte "usage record". 54 bytes is this header plus two rows, and the
offsets it found changing fall on the rows' fields. In the second row (the RD, which is the hub):

- `+20` rose by exactly 22 over 22 shifts: the actuation count.
- The low half of `+16` rose about once a second: runtime as seconds awake.
- `+8` varied constantly: millivolts.

The value it called "shift-linked" is the first row's `+8`: the controller's coin-cell millivolts, dipping
with button presses. Of the 29 characteristics it could read, this was the only one that changed in use.
[PA] (GaborWnuk), [INF] the row mapping

**`d9050004`: firmware table**, 16-byte rows [HW] [APP]. (`d9050002` is an older 15-byte form without the
last byte. [APP])

| Offset | Field | Notes |
|---|---|---|
| +0 | device type u8 | |
| +1 | model id u16 | |
| +3 | serial u32 | |
| +7 | app version, 4 bytes | Version is bytes `[+10].[+9].[+8]`; `+7` is 0. (If only the low byte is non-zero, SRAM shows that one number.) [APP] |
| +11 | SoftDevice number u16 | Nordic stack: 332 (S332) on the RD, 0 in the hub's rows for FD and shifters |
| +13 | bootloader u16 | Shown as `hi.lo`: `04 01` → "1.4" [HW]; field identity [INF] |
| +15 | bridge protocol version u8 | RD `01`, FD `00`, shifters `ff` (not applicable) |

```
02 04 6e 7a c1 53
81 2a 04 6e 7a c1 53 00 08 37 02 4c 01 04 01 01   RD 2.55.8, S332, bootloader 1.4, bridge 1
80 ea 03 cb 66 63 4e 00 03 07 02 00 00 00 00 00   FD 2.7.3
01 ec 03 f2 fe 7a 43 00 03 07 02 00 00 00 00 ff   R shifter 2.7.3
00 ed 03 31 2e 7b 43 00 03 07 02 00 00 00 00 ff   L shifter 2.7.3
```

### 5.2 Standard Battery Level `0x2A19`

| Component | Observation | Conf. |
|---|---|---|
| Quarq DZero | Answers `0x2A19` (percent) unpaired. This is how Cassette gets its battery | [HW] |
| Our RD | Exposes `0x2A19`. The official app read it in plaintext | [HW] |
| Our Red shifters | **No** `0x180F` service: only `1800`, `1801` and the SRAM `FE51` service with static id characteristics | [HW] |
| Some other AXS controllers | Expose `0x180F` / `0x2A19` unauthenticated | [PA] (sram-axs-for-ha) |
| New-generation pod (1073) | Has a `0x180F` service, and `0x2A19` (read, notify) gave 60 % in the tester's nRF Connect reads. Its adverts carry no battery; Cassette gets it from the hub's table instead (2701 mV, OK) | [PA] (tester), [HW] table |
| Our FD | Refuses connections (Android status 147) | [HW] |

### 5.3 Per-component `fe5x` characteristics

On the shifters these sit under the `FE51` service. SRAM's app also knows them as `d905fe5x`. [HW] [APP]
Cassette treats all of them as read-only.

| UUID | Purpose | Conf. |
|---|---|---|
| `fe53` | Unknown | [?] |
| `fe54` | Serial | [HW] read, [INF] meaning |
| `fe56` | Model id (`ec 03` = 1004 on the right shifter) | [HW] read, [INF] meaning |
| `fe57` | **Enter DFU.** Reads `aa`. Writing it starts a firmware update. **Never write** | [APP] [HW] read |
| `fe58` | Version block (24+ bytes): format tag, app version, bootloader, SoftDevice number and version, hardware id, then an optional NUL-terminated `rev` string (`g99fff42034.dir` on the RD). The firmware is bytes 5–7, patch first: `06 37 02` = 2.55.6, as SRAM's app showed | [APP] [HW]; offset [PA] (GaborWnuk) |
| `fe60` | Battery status: `mV u16`, `soc u8`, then one signed byte that's either percent or temperature. Not present on our shifters | [APP], byte 3 [?] |
| `fe61` | Usage: two u32 (actuation count and runtime, order unconfirmed) | [APP] |

Nordic DFU services exist on these devices too. Never write to them either. [APP]

The tester read the new-generation pod with nRF Connect. It has `1800`, `1801`, `FE51` (`fe53`–`fe58`) and
`180F`: `fe53` seven zero bytes, `fe54` the serial, `fe56` `31 04` (1073), `fe57` `aa`, and `fe58`:

```
01 00 00 00 00 01 25 02 00 00 01 01 70 00 00 00 00 00 02 07 20 28 05 00
```

Bytes 5–7 give 2.37.1, the firmware in its adverts. `70 00` may be the SoftDevice number (S112), and the
last four bytes read as 0x00052820, Nordic's part number for an nRF52820. [PA] (tester), [INF] the last two

### 5.4 The authenticated session (`ee52`)

The RD exposes a bonding/session characteristic, `d905ee52`. The official app runs a key-agreement handshake
on it, and from then on most configuration and telemetry characteristics read back encrypted (shift
reactions, drivetrain status, multishift, trim and similar). [HW] [APP] Cassette **deliberately doesn't
use it** and this doc doesn't describe it. Everything Cassette needs is available without it.

What's readable without the session:

| Characteristic | Conf. |
|---|---|
| `d9050003`, `d9050004` (and `d9050002`) | [HW] read unauthenticated by Cassette |
| `0x2A19` on the Quarq | [HW] |
| `fe54`, `fe56`, `fe57` on the shifters | [HW] |
| `fe58`, `0x2A19` on the RD, `d905000a` drivetrain config | [HW] read unauthenticated from iOS (2026-10-18): `fe58` as in §5.3, `2a19` = 72, `d905000a` as below |
| Device Information `0x180A` (`2a24`–`2a29`, `2a50`) | [APP] |

`d905000a` (drivetrain config) holds varint fields: FD gears, RD gears, RD trim positions. Our bike reads
`b8 01 02 | c0 01 0c | c8 01 1d` = 2 × 12, 29 trim steps. [HW] decode, [INF] field names

### 5.5 UUID map summary

| UUID | Service / role |
|---|---|
| `d9050001` | SRAM system service: `0002` firmware v1, `0003` battery table, `0004` firmware v2 |
| `d9050005` | Drivetrain: `0006` enhanced mode, `0009`/`000b` drivetrain status, `000a` config, `03f4` FD trim (encrypted except `000a`) |
| `d905000f`…`0070` | Reaction and shift configs for FD, RD, multishift, seatpost, controllers, Flight Attendant (encrypted) |
| `d905009a`…`0a00`, `03fa`, `ef00`, `fff0` | Powertrain, ANT, lights, e-bike, third-party shifting, peer discovery (not seen on our bike) |
| `d905ee52` | Authenticated session (§5.4) |
| `d905fff2` | Identity as protobuf (holds the serial), plaintext [PA] |
| `adee000x-7726/7727-453c-a069-007ea97a0add` | A second vendor family on a GX Transmission RD, purpose unknown [PA] |
| `0xFE51` (16-bit) | SRAM advert service data; GATT service holding `fe53`–`fe61` |
| `0x1818`, `0x180F`/`0x2A19`, `0x180A` | Standard cycling power, battery, device information. The tester's Quarq has all three; a GX Transmission RD has no `0x180A` [PA] |

[APP] for the map; [HW] for `0003`, `0004`, `ee52`, `FE51`, `1818`, `2A19`; [PA] (GaborWnuk, tester) where marked.

## 6. Battery semantics

**Reported status (soc)**, the same byte in the advert `0x0400` record, hub-table rows and `fe60`:

| soc | Status | SRAM's app shows | Conf. |
|---|---|---|---|
| 0 (or negative) | Unknown | — | [APP] |
| 1 | Critical | "Charge now (<5%)" | [APP] |
| 2 | Low | "Battery low (<20%)" | [APP] |
| 3 | OK | "Battery good" | [APP] [HW] (every component on our bike) |

For AXS components SRAM's app shows **only this status**, never a percentage (only Powertrain displays show
one). [APP]

The component decides the status, with hysteresis. As the tester's RD drained, it still reported OK at
22 % and low at 20 %. After charging it was still low at 22 % and back to OK at 23 %. That fits SRAM's
"<20%" label. [PA] (tester)

**Thresholds SRAM uses when there's no reported status** [APP]:

| Input | OK | Low | Critical |
|---|---|---|---|
| Battery Level `0x2A19` % | > 20 | 6–20 | 1–5 (0 = unknown) |
| Coin-cell voltage (only when soc = 0) | > 2.4 V | > 2.2 V | ≤ 2.2 V |
| AXS pack voltage | — no curve exists in SRAM's app — | | |

Prior art suggests stricter coin-cell alerts (warn ~2.80 V, critical ~2.75 V), and observed the official app
saying "Battery good" at ~2.7 V. [PA] Cassette follows SRAM's thresholds (decision 0003).

**Pack voltage estimate (ours).** Packs report a percent in the `0x0400` record, but the hub table only has
mV. Cassette interpolates from advert (mV, %) pairs and shows the result as "≈" [INF]:

| mV | % | Source |
|---|---|---|
| 7479 | 19 | esphome capture |
| 7523 | 23 | esphome capture |
| 7881 | 81 | our RD |
| 8118 | 100 | esphome capture |

Below 7479 mV it extrapolates the first segment's slope down to 0. Cassette treats anything ≥ 5000 mV as a
pack. [HW]

The tester's packs agree with the curve [HW] (tester): fork 7722 mV → 55 % (the curve says 55), dropper
7579 → 32 (32), rear shock 8098 → 100 (98), RD 8265 → 100.

**The bottom of the curve.** The tester logged their fork draining from 73 % to empty over 12 days (the
percent from its adverts) [PA] (tester):

| mV | 7830 | 7720 | 7610 | 7520 | 7430 | 7410 | 7370 | 7360 |
|---|---|---|---|---|---|---|---|---|
| SRAM % | 73 | 55 | 37 | 23 | 15 | 14 | 10 | **0** |
| Our curve | 72 | 54 | 37 | 22 | 14 | 12 | 9 | 8 |

The curve holds to within 2 points down to 7370 mV. Then SRAM drops from 10 % straight to 0, while the
curve still says 8 % and reaches 0 only near 7280 mV. 0 % isn't flat: the fork kept working, fell to
6.47 V within 8 minutes of use, and switched off near 6.30 V.

**Chemistry per type** (SRAM's app labels) [APP]:

| Types | Battery |
|---|---|
| Shifters, controllers, BlipBox, pods, combos, AirWiz | CR2032, removable |
| Wireless Blips | CR2016, not removable |
| RD, FD, fork, shock, dropper | AXS battery pack (~8 V) [HW for RD/FD; fork, shock and dropper on the tester's MTB]. 2S lithium-ion polymer, 300 mAh, 7.4 V nominal, 8.4 V full, 6.0 V end of discharge, per SRAM's battery manual as the tester quotes it [PA] |
| Pedal sensor, spindle, power meters | "Lithium AAA" label |

SRAM's app also gives remaining-time hints when low: months for coin-cell controllers, hours for packs (RD
"<1.5–5 h", FD and dropper "<6–15 h"). [APP]

## 7. Identity

| Item | Detail | Conf. |
|---|---|---|
| Component serial | u32, shown in decimal in the `SRAM <serial>` name. Same value in the service frame and the hub tables | [HW] |
| Bike / system id | `masterSerial` in both hub tables = the hub's own serial (RD 1405188718). Every paired component is a row under it. A bike can hold more than one system (§2; decision 0028) | [HW] |
| Bridge flag | `flags2` bit0 in the hub's service frame | [HW] [APP] |
| Quarq serial | Shown as 3 letters + 5 digits. In our capture the advert's raw serial `0x00cd3063` has low 16 bits 0x3063 = **12387**, matching `Quarq AGN12387`; the tester's `0x00c3ede3` gives 60899, matching `Quarq AGD60899`. How the letters are encoded is unknown. SRAM swaps the 16-bit halves for models 7/8/9 | [HW] digits, [APP] swap, [?] letters |
| Quarq key in Cassette | The name, since Quarq ids aren't SRAM decimal serials | [HW] |
| BLE address | Stable for the RD within and across sessions so far, which lets serial-less RD frames be matched by address. On the tester's MTB every component kept one address through a 25 h capture. Unverified across battery swaps and firmware updates | [HW] so far, [?] long-term |

## 8. Open questions

1. What record `0x0000` means (value `02`; `01` before an update per prior art), and the constant `0x80` in
   record `0x0400`.
2. Whether the trailing byte after the `0x0101` / `0x0402` records is a rotation index.
3. Why our RD's hub-table "runtime" (+16) is implausible when the tester's dropper and prior art's GX RD
   count seconds awake (§5.1). Also the always-zero update time (+12), and whether the FD's 2000
   actuations is real.
4. Is the BLE address stable across days, battery swaps and firmware updates? (A day so far: §7.) This
   matters for CompanionDeviceManager presence (decision 0009).
5. ~~Can `fe58`, `0x2A19` (RD) and `d905000a` be read without the session?~~ Yes (§5.4).
6. Is `fe60` byte 3 percent or temperature? What's in `fe53`? What order are the `fe61` fields in?
7. Do the Red shifters and FD send status frames (with battery) in any mode, or only beacons? The older
   AXS controller sends status frames, and the new-generation pod only beacons. ~~Does anything other than
   the RD act as hub?~~ Yes: droppers and rear shocks (§2).
8. Does a connected bike computer (not just SRAM's app) silence adverts?
9. How are Quarq serial letters encoded, and what are the Quarq frame's model flags and leading version bytes?
10. Shifter/FD firmware reports 2.7.3, while SRAM's published production firmware for those model ids
    is 2.55.x. Different numbering, or genuinely old firmware?
11. Which coin-cell controllers expose `0x180F` (prior art, and the new-generation pod per the tester's
    nRF Connect reads) and which don't (our Red shifters)?
12. Does advertising activity (shifting) extend the pack wake window, and by how much?
13. What's in the rear shock's tables on the tester's MTB (the RD and the older controller, we expect)? How
    does SRAM's app show two systems on one bike, and what is the "family" its pod edge case reads (§4.1)?
    Which side is the tester's new pod on, and what does SRAM's app call it?
14. What's the 8-byte frame without a device type, `0d 00 <serial> 00 82`, that the tester's fork sent
    (§3.3)? When is it sent?
