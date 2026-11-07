# Shimano Di2 Bluetooth notes

What public projects say about how 12-speed Shimano Di2 talks over Bluetooth LE, written in our own
words (decision [0029](decisions/0029-shimano-di2-diagnostics.md)). This is **unofficial** and
**unverified**: nobody on the project has a Di2 bike, so nothing here is confirmed on hardware.
`docs/protocol.md` covers SRAM AXS and uses the same tags; everything below is **[PA]** (prior art)
or **[INF]** (inferred) until a tester's capture says otherwise.

## 1. Sources

| Source | What it gave us |
|---|---|
| [diegogalindez/Galo](https://github.com/diegogalindez/Galo) (Home Assistant) | The system battery from the standard Battery Level (`0x2A19`) with no pairing, a connect–read–close every 30 min; how it finds the derailleur. Tested only on an RD-R9250 |
| [kelvkk/shimano-di2-media-controls](https://github.com/kelvkk/shimano-di2-media-controls) | The `SHIMANO_BLE` service UUID; D-Fly button indications, which need a bond |
| [rpad300/KROMI_BIKECONTROL](https://github.com/rpad300/KROMI_BIKECONTROL) `docs/SHIMANO_STEPS_PROTOCOL.md` | The SBI service's characteristics, for Shimano's STEPS e-bike motors |
| [OpenBikeControl/bikecontrol](https://github.com/OpenBikeControl/bikecontrol) `shimano_di2.dart` | The `18FF` E-TUBE service on an RD-R7150, and that its session leaves the derailleur in "PC-connect" mode |
| [Shimano UM-2710B](https://si.shimano.com/en/pdfs/um/2710B/UM-2710B.pdf) | What the wireless unit sends computers (gears, one battery level, adjustment mode); that the RD doesn't shift while connected in E-TUBE's connection mode |

## 2. The system

| Claim | Conf. |
|---|---|
| On 12-speed Di2 the rear derailleur holds the Bluetooth (and ANT) radio. Shifters talk to it over Shimano's own 2.4 GHz link, not Bluetooth, so they're never heard directly | [PA] |
| One rechargeable battery powers both derailleurs; shifters run on coin cells | [PA] |
| 11-speed Di2 needs a D-Fly unit (EW-WU111, EW-WU101) to have Bluetooth at all | [PA] |
| Computers get a single system battery level, not one per component | [PA] (Shimano's manual) |

## 3. Advertisements

| Marker | Detail | Conf. |
|---|---|---|
| Company id | `0x044A` in manufacturer data | [PA] |
| Service UUIDs | Shimano's end in `-5348-494d-414e-4f5f424c4500`, ASCII `SHIMANO_BLE\0`: `000018ef-…` (SBI) and `000018ff-…` (E-TUBE) | [PA] |
| Name | Rear derailleurs advertise as `RDR` + model (`RDR9250 …`) | [PA] |
| Battery in the advert | Unknown. Nobody has published one | [?] |
| Wake behaviour | The derailleur advertises only while awake; prior art connects when it is | [PA] |

`ShimanoAdvertParser` matches any of the three markers and keeps the advert raw.

## 4. GATT

| Service | Access | What's there | Cassette | Conf. |
|---|---|---|---|---|
| Battery (`0x180F`) | Open, no pairing | Battery Level `0x2A19`: the system battery, percent | Diagnostics' probe reads it | [PA] |
| Device Information (`0x180A`) | Open | Maker, model, firmware and so on | The probe reads them, but not the serial number | [INF] |
| SBI (`18EF`), "Shimano Bicycle Information" | Standard bonding | Feature `2AC0`, Periodic Information `2AC1` (notify), Instantaneous `2AC2`, D-Fly `2AC3`, Control Point `2AC4`, System Serial `2AC5`. On STEPS motors, Periodic Information carries assist, speed and range; nobody has published Di2's layout. Probably what Bluetooth computers read | Not yet. Needs a bond (a system pairing prompt), which the read-only GATT client doesn't do | [PA] layout on STEPS, [INF] for Di2 |
| E-TUBE (`18FF`) | The bike's passkey plus keys derived from its serials | Settings and probably detail for each component. Its session puts the derailleur in PC-connect mode, where it doesn't shift | **Never.** Decision 0029 | [PA] |

## 5. Open questions

1. Do Di2 adverts carry battery, or anything that changes with it?
2. Does a plain connection (the probe's) leave shifting alone? Computers connect over Bluetooth and
   shifting works, but nobody has said so for a phone.
3. Does `0x2A19` report in 1 % steps, or the 20 % bands Shimano's displays use?
4. Does SBI's Periodic Information carry more battery detail than `0x2A19`, such as a low shifter
   coin cell? And how many bonds does a derailleur keep?
5. Do the GRX and mountain groups advertise the same way as the road derailleurs?
