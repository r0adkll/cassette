package com.r0adkll.cassette.axs

import kotlinx.serialization.Serializable

/** One decoded advertisement from an AXS component. */
@Serializable
data class AxsReading(
  /** Stable identity: the component serial when we could find one, otherwise the BLE address. */
  val key: String,
  val serial: Long?,
  val address: String,
  val name: String?,
  val rssi: Int,
  val timestampMillis: Long,
  val frame: FrameType,
  val kind: ComponentKind,
  /** Only status frames carry battery; beacon and power meter frames need a GATT read. */
  val battery: AdvertBattery?,
  /** Application firmware, e.g. "2.55.8", from the SRAM service data or the version record. */
  val firmware: String?,
  val manufacturerHex: String?,
  val serviceDataHex: String?,
  /** SRAM device type (the "class" byte), when the frame carries it. */
  val classByte: Int? = null,
  /** SRAM model id (u16), e.g. 1004 for a right drop-bar shifter. */
  val modelId: Int? = null,
  /**
   * The component advertising itself as the system's BLE bridge (the rear derailleur on AXS road).
   */
  val bridge: Boolean = false,
)

@Serializable
data class AdvertBattery(
  val millivolts: Int,
  val percent: Int?,
  /** The component's own battery state, as SRAM's app shows it ("Battery good"). */
  val reportedStatus: BatteryStatus? = null,
)

enum class FrameType {
  /**
   * Manufacturer data `0x0933` with a battery record, plus SRAM service data. Seen from derailleurs
   * and droppers.
   */
  STATUS,
  /**
   * SRAM service data with serial, model and firmware but no battery. Shifters and front derailleur
   * after waking.
   */
  BEACON,
  /** Quarq power meter: cycling power service plus its own `0xFE51` payload. */
  POWER_METER,
  /** Not heard directly; known only from the rear derailleur's battery table. */
  HUB_ENTRY,
}

enum class ComponentKind(val label: String) {
  LEFT_SHIFTER("Left shifter"),
  RIGHT_SHIFTER("Right shifter"),
  BLIPBOX("BlipBox"),
  LEFT_REVERB_CONTROLLER("Left Reverb controller"),
  RIGHT_REVERB_CONTROLLER("Right Reverb controller"),
  LEFT_FLATBAR_CONTROLLER("Left controller"),
  RIGHT_FLATBAR_CONTROLLER("Right controller"),
  WIRELESS_BLIPS("Wireless Blips"),
  LEFT_POD("Left pod"),
  RIGHT_POD("Right pod"),
  PEDAL_SENSOR("Pedal sensor"),
  AIRWIZ("AirWiz"),
  FRONT_DERAILLEUR("Front derailleur"),
  REAR_DERAILLEUR("Rear derailleur"),
  FORK("Fork"),
  REAR_SHOCK("Rear shock"),
  DROPPER_POST("Dropper post"),
  POWER_METER("Power meter"),
  POWERTRAIN("Powertrain"),
  /** Kept so data saved before left/right decoding still loads. */
  CONTROLLER("Controller"),
  UNKNOWN("AXS component");

  companion object {
    /** SRAM device type byte (`SramDeviceType` in SRAM's app). */
    fun fromDeviceType(value: Int?): ComponentKind =
      when (value) {
        null -> UNKNOWN
        0 -> LEFT_SHIFTER
        1 -> RIGHT_SHIFTER
        2 -> BLIPBOX
        3 -> LEFT_REVERB_CONTROLLER
        4 -> RIGHT_REVERB_CONTROLLER
        5 -> PEDAL_SENSOR
        6 -> AIRWIZ
        7 -> LEFT_FLATBAR_CONTROLLER
        8 -> RIGHT_FLATBAR_CONTROLLER
        in 96..113 -> WIRELESS_BLIPS
        // Pods don't encode side in their type; SRAM resolves it from parity.
        in 114..125 -> if (value % 2 == 0) LEFT_POD else RIGHT_POD
        0x80 -> FRONT_DERAILLEUR
        0x81 -> REAR_DERAILLEUR
        0x82 -> FORK
        0x83 -> REAR_SHOCK
        0x84 -> DROPPER_POST
        0x85 -> POWER_METER
        in 134..139 -> POWERTRAIN
        else -> UNKNOWN
      }

    /**
     * Model id → kind, from SRAM's `DeviceUtils::guess_device_type`. Used when a frame has no
     * device type byte.
     */
    fun fromModelId(value: Int?): ComponentKind =
      when (value) {
        null -> UNKNOWN
        7,
        in 70..75,
        in 100..110,
        1052 -> POWER_METER
        1000,
        1001,
        1013,
        1014,
        1033,
        1036,
        1056,
        1066,
        1070,
        1071,
        1074,
        1075,
        1081,
        1082 -> REAR_DERAILLEUR
        1002,
        1003,
        1037,
        1045,
        1051 -> FRONT_DERAILLEUR
        1004,
        1006,
        1008,
        1010,
        1035,
        1047,
        1083,
        1091,
        1093,
        1095,
        1097,
        1099,
        1113 -> RIGHT_SHIFTER
        1005,
        1007,
        1009,
        1011,
        1034,
        1046,
        1084,
        1090,
        1092,
        1094,
        1096,
        1098,
        1112 -> LEFT_SHIFTER
        1012 -> BLIPBOX
        1015,
        1016 -> RIGHT_REVERB_CONTROLLER
        1017,
        1044 -> LEFT_REVERB_CONTROLLER
        in 1018..1029,
        in 1067..1069 -> DROPPER_POST
        1038,
        1048,
        1049 -> FORK
        1039 -> REAR_SHOCK
        1040 -> PEDAL_SENSOR
        else -> UNKNOWN
      }
  }
}

enum class BatteryStatus {
  OK,
  LOW,
  CRITICAL,
  UNKNOWN;

  companion object {
    /** The `soc` byte components report: 1 critical, 2 low, 3 ok; anything else is unknown. */
    fun fromSoc(value: Int): BatteryStatus? =
      when (value) {
        1 -> CRITICAL
        2 -> LOW
        3 -> OK
        else -> null
      }
  }
}

/**
 * Our own status for when a component doesn't report one. Thresholds are SRAM's app's: Battery
 * Level percent
 * > 20 ok, 6–20 low, 1–5 critical, 0 unknown; coin cells (its only voltage thresholds) ok > 2.4 V,
 * > low > 2.2 V. Packs have no SRAM voltage curve, so we use our estimate.
 */
fun batteryStatus(millivolts: Int?, percent: Int?): BatteryStatus =
  when {
    percent != null && percent > 0 ->
      when {
        percent <= 5 -> BatteryStatus.CRITICAL
        percent <= 20 -> BatteryStatus.LOW
        else -> BatteryStatus.OK
      }
    millivolts != null && millivolts >= PACK_MIN_MILLIVOLTS ->
      batteryStatus(null, estimatePackPercent(millivolts))
    millivolts != null ->
      when {
        millivolts <= 2_200 -> BatteryStatus.CRITICAL
        millivolts <= 2_400 -> BatteryStatus.LOW
        else -> BatteryStatus.OK
      }
    else -> BatteryStatus.UNKNOWN
  }

/** Anything above this is an AXS battery pack (~8 V); below is a coin cell (~3 V). */
const val PACK_MIN_MILLIVOLTS = 5_000
