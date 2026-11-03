package com.r0adkll.cassette.capture.api

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.protocol.AdvertBattery
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.ComponentVersions
import com.r0adkll.cassette.protocol.SystemComponent
import com.r0adkll.cassette.protocol.SystemTable
import kotlinx.coroutines.flow.Flow

/** Finding and adding a bike (decision 0010, add bike). */
interface BikeDiscovery {
  /**
   * A live scan for hubs (the component that reports for a system) and power meters that aren't on
   * a bike yet. Emits everything found so far, strongest signal first.
   */
  fun discover(): Flow<Discovered>

  /**
   * Reads the hub's battery and firmware tables; null if the read failed (wake the bike and retry).
   */
  suspend fun readHub(hub: FoundHub): HubDetails?

  /**
   * Saves the bike with one system per hub in [hubs] (the first is the one it's known by) and every
   * component their tables list, plus [powerMeter]; arms capture.
   */
  suspend fun addBike(
    name: String,
    type: BikeType,
    hubs: List<HubDetails>,
    powerMeter: FoundPowerMeter?,
  ): BikeId

  /** Adds a system per hub in [hubs], with the components their tables list, to [bike]. */
  suspend fun addSystems(bike: BikeId, hubs: List<HubDetails>)
}

data class Discovered(
  val hubs: List<FoundHub>,
  val powerMeters: List<FoundPowerMeter>,
  /** Hubs heard that are a bike's systems already, strongest first. */
  val known: List<KnownHub> = emptyList(),
)

/** A hub that is one of [bike]'s systems already. */
data class KnownHub(val hub: FoundHub, val bike: BikeId)

data class FoundHub(
  val serial: Long,
  val address: String,
  val kind: ComponentKind,
  val modelId: Int?,
  val rssi: Int,
  val battery: AdvertBattery?,
  val firmware: String?,
)

data class FoundPowerMeter(val name: String, val address: String, val modelId: Int?, val rssi: Int)

data class HubDetails(
  val hub: FoundHub,
  val table: SystemTable<SystemComponent>,
  val versions: SystemTable<ComponentVersions>?,
) {
  /** The table's rows with firmware, hub first. */
  val components: List<HubComponent>
    get() {
      val firmware = versions?.rows?.associate { it.serial to it.firmware }.orEmpty()
      return table.rows
        .map {
          HubComponent(
            serial = it.serial,
            kind = it.kind,
            millivolts =
              if (it.serial == table.masterSerial) hub.battery?.millivolts ?: it.millivolts
              else it.millivolts,
            percent = if (it.serial == table.masterSerial) hub.battery?.percent else null,
            status = it.reportedStatus,
            firmware = firmware[it.serial],
          )
        }
        .sortedByDescending { it.serial == table.masterSerial }
    }
}

data class HubComponent(
  val serial: Long,
  val kind: ComponentKind,
  val millivolts: Int,
  val percent: Int?,
  val status: BatteryStatus?,
  val firmware: String?,
)
