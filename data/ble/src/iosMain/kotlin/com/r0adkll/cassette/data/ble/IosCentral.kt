package com.r0adkll.cassette.data.ble

import com.r0adkll.cassette.protocol.AxsAdvertParser
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.toHex
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionRestoreIdentifierKey
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/** Bluetooth as the app sees it: whether it may scan, and whether the radio is on. */
enum class BluetoothState {
  /** Never asked: creating the central manager shows the system prompt. */
  NotAsked,
  Denied,
  Off,
  On,
  /** Allowed, and the radio's state hasn't arrived yet. */
  Starting,
}

/**
 * The app's one `CBCentralManager` (decision 0017). It runs on the main queue, is created with a
 * restore identifier so iOS can relaunch the app for the background scan, and holds one scan at a
 * time: unfiltered while anything collects [live], otherwise the `FE51` scan if background capture
 * is armed. Read-only: it never writes a characteristic.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@Inject
class IosCentral(private val deliveries: Lazy<BackgroundDeliveryHandler>) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val defaults = NSUserDefaults.standardUserDefaults
  private val centralDelegate = CentralDelegate(this)
  private val peripheralDelegate = PeripheralDelegate(this)

  private var manager: CBCentralManager? = null
  private val liveListeners = mutableSetOf<SendChannel<AxsReading>>()
  private val pendingDeliveries = mutableListOf<AxsReading>()
  private val sessions = mutableMapOf<String, GattSession>()

  private val _state = MutableStateFlow(currentState())
  val state: StateFlow<BluetoothState> = _state

  /** Survives relaunches: iOS relaunches the app for the scan without anyone re-arming it. */
  private var armed: Boolean
    get() = defaults.boolForKey(ARMED_KEY)
    set(value) = defaults.setBool(value, ARMED_KEY)

  /**
   * Called at launch. Creates the manager if Bluetooth was asked about before, so a relaunch for
   * the background scan gets its restored state; otherwise waits for [requestPermission], so the
   * prompt comes after onboarding explains it.
   */
  fun start() {
    if (CBManager.authorization != CBManagerAuthorizationNotDetermined) ensureManager()
  }

  /** Shows the system prompt if Bluetooth was never asked about. */
  fun requestPermission() = ensureManager()

  private fun ensureManager(): CBCentralManager =
    manager
      ?: CBCentralManager(
          delegate = centralDelegate,
          queue = dispatch_get_main_queue(),
          options =
            mapOf<Any?, Any?>(
              CBCentralManagerOptionRestoreIdentifierKey to RESTORE_ID,
              CBCentralManagerOptionShowPowerAlertKey to false,
            ),
        )
        .also { manager = it }

  private fun currentState(): BluetoothState =
    when (CBManager.authorization) {
      CBManagerAuthorizationNotDetermined -> BluetoothState.NotAsked
      CBManagerAuthorizationAllowedAlways ->
        when (manager?.state) {
          CBManagerStatePoweredOn -> BluetoothState.On
          CBManagerStatePoweredOff -> BluetoothState.Off
          null,
          CBManagerStateUnknown,
          CBManagerStateResetting -> BluetoothState.Starting
          else -> BluetoothState.Off
        }
      else -> BluetoothState.Denied
    }

  /** Waits briefly for the radio's state after launch; true once it's on. */
  private suspend fun awaitOn(): Boolean {
    ensureManager()
    val settled = withTimeoutOrNull(3.seconds) { state.first { it != BluetoothState.Starting } }
    return settled == BluetoothState.On
  }

  // Scanning

  internal suspend fun addLiveListener(channel: SendChannel<AxsReading>): Boolean =
    withContext(Dispatchers.Main) {
      if (!awaitOn()) return@withContext false
      liveListeners += channel
      updateScan()
      true
    }

  internal fun removeLiveListener(channel: SendChannel<AxsReading>) {
    scope.launch {
      liveListeners -= channel
      updateScan()
    }
  }

  internal fun arm(): ArmResult {
    when (state.value) {
      BluetoothState.NotAsked,
      BluetoothState.Denied -> return ArmResult.NoPermission
      BluetoothState.Off -> return ArmResult.BluetoothOff
      // Starting arms anyway: the scan begins once the radio reports in.
      BluetoothState.On,
      BluetoothState.Starting -> Unit
    }
    armed = true
    scope.launch { updateScan() }
    return ArmResult.Armed
  }

  internal fun disarm() {
    armed = false
    scope.launch { updateScan() }
  }

  private fun updateScan() {
    val manager = manager?.takeIf { it.state == CBManagerStatePoweredOn } ?: return
    manager.stopScan()
    val options = mapOf<Any?, Any?>(CBCentralManagerScanOptionAllowDuplicatesKey to true)
    when {
      // Unfiltered, so a power meter advertising as a plain sensor still shows up.
      liveListeners.isNotEmpty() -> manager.scanForPeripheralsWithServices(null, options)
      // The only scan iOS keeps running in the background; it ignores duplicates there.
      armed -> manager.scanForPeripheralsWithServices(listOf(SRAM_SERVICE), options)
    }
  }

  private fun onDiscover(peripheral: CBPeripheral, advertisement: Map<Any?, *>, rssi: Int) {
    val name = advertisement[CBAdvertisementDataLocalNameKey] as? String ?: peripheral.name
    // Core Bluetooth merges every 0x0933 structure into one blob (the spike, decision 0017).
    val manufacturer =
      (advertisement[CBAdvertisementDataManufacturerDataKey] as? NSData)
        ?.toByteArray()
        ?.takeIf { it.size >= 2 && it.u16le(0) == AxsAdvertParser.COMPANY_ID }
        ?.let { it.copyOfRange(2, it.size) }
    val serviceData =
      (advertisement[CBAdvertisementDataServiceDataKey] as? Map<*, *>)
        ?.entries
        ?.firstOrNull { (it.key as? CBUUID)?.UUIDString == SRAM_SERVICE.UUIDString }
        ?.let { (it.value as? NSData)?.toByteArray() }
    if (manufacturer == null && serviceData == null) return
    val reading =
      AxsAdvertParser.parse(
        name = name,
        manufacturer = manufacturer ?: ByteArray(0),
        serviceData = serviceData,
        rssi = rssi,
        address = peripheral.identifier.UUIDString,
        timestampMillis = Clock.System.now().toEpochMilliseconds(),
      ) ?: return
    // Raw bytes stay in the device log for diagnostics (decision 0005).
    println(
      "$TAG ${reading.key} ${reading.frame} ${reading.battery?.millivolts ?: "-"}mV " +
        "id=${reading.address} mfg=[${manufacturer?.toHex().orEmpty()}] " +
        "svc=[${serviceData?.toHex().orEmpty()}]"
    )
    liveListeners.forEach { it.trySend(reading) }
    if (armed) queueDelivery(reading)
  }

  /**
   * Background discoveries arrive one at a time; they go to the handler in small batches, inside a
   * background task so its hub reads can finish after iOS would otherwise suspend the app.
   */
  private fun queueDelivery(reading: AxsReading) {
    pendingDeliveries += reading
    if (pendingDeliveries.size > 1) return
    scope.launch {
      delay(BATCH_WINDOW)
      val batch = pendingDeliveries.toList()
      pendingDeliveries.clear()
      val app = UIApplication.sharedApplication
      var task = UIBackgroundTaskInvalid
      task =
        app.beginBackgroundTaskWithName("cassette.delivery") {
          scope.launch(Dispatchers.Default) { deliveries.value.onBudgetExceeded() }
          app.endBackgroundTask(task)
          task = UIBackgroundTaskInvalid
        }
      try {
        withContext(Dispatchers.Default) { deliveries.value.onDelivery(batch, 0) }
      } finally {
        if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
      }
    }
  }

  // GATT

  internal suspend fun read(
    address: String,
    characteristics: List<String>,
    timeout: Duration,
  ): GattResult =
    withContext(Dispatchers.Main) {
      if (!awaitOn() || address in sessions) return@withContext GattResult.Failed
      val manager = manager ?: return@withContext GattResult.Failed
      // Addresses are identifier UUIDs on iOS; anything else came from another platform.
      if (!IDENTIFIER.matches(address)) return@withContext GattResult.Failed
      val peripheral =
        manager
          .retrievePeripheralsWithIdentifiers(listOf(NSUUID(uUIDString = address)))
          .firstOrNull() as? CBPeripheral ?: return@withContext GattResult.Failed
      val session = GattSession(peripheral, characteristics)
      sessions[address] = session
      peripheral.delegate = peripheralDelegate
      manager.connectPeripheral(peripheral, null)
      try {
        withTimeoutOrNull(timeout) { session.result.await() }
          ?: GattResult.Failed.also { println("$TAG GATT $address timed out") }
      } finally {
        sessions.remove(address)
        manager.cancelPeripheralConnection(peripheral)
      }
    }

  private fun session(peripheral: CBPeripheral) = sessions[peripheral.identifier.UUIDString]

  private class GattSession(val peripheral: CBPeripheral, requested: List<String>) {
    /** Normalised UUID to the caller's spelling, which keys the result. */
    val wanted = requested.associateBy(::normalise)
    val values = mutableMapOf<String, ByteArray>()
    var services = emptyList<String>()
    var pending = 0
    val result = CompletableDeferred<GattResult>()

    fun finishIfDone() {
      if (pending == 0) result.complete(GattResult.Values(values, services))
    }
  }

  private fun onConnected(peripheral: CBPeripheral) {
    val session = session(peripheral) ?: return
    // Only the services we read: discovering all 20 of the RD's ran past the timeout (the spike).
    val services = session.wanted.keys.map { KNOWN_SERVICES[it] }
    peripheral.discoverServices(
      if (services.any { it == null }) null else services.filterNotNull().distinct().map(::cbuuid)
    )
  }

  private fun onServices(peripheral: CBPeripheral, error: NSError?) {
    val session = session(peripheral) ?: return
    val services = peripheral.services.orEmpty().filterIsInstance<CBService>()
    if (error != null || services.isEmpty()) {
      session.result.complete(GattResult.Failed)
      return
    }
    session.services = services.map { normalise(it.UUID.UUIDString) }
    session.pending = services.size
    services.forEach { peripheral.discoverCharacteristics(null, it) }
  }

  private fun onCharacteristics(peripheral: CBPeripheral, service: CBService) {
    val session = session(peripheral) ?: return
    val readable =
      service.characteristics.orEmpty().filterIsInstance<CBCharacteristic>().filter {
        normalise(it.UUID.UUIDString) in session.wanted &&
          it.properties and CBCharacteristicPropertyRead != 0uL
      }
    session.pending += readable.size - 1
    readable.forEach { peripheral.readValueForCharacteristic(it) }
    session.finishIfDone()
  }

  private fun onValue(peripheral: CBPeripheral, characteristic: CBCharacteristic, error: NSError?) {
    val session = session(peripheral) ?: return
    val key = session.wanted[normalise(characteristic.UUID.UUIDString)]
    val value = characteristic.value
    if (key != null && error == null && value != null) session.values[key] = value.toByteArray()
    println("$TAG GATT ${peripheral.identifier.UUIDString} read $key error=${error?.code}")
    session.pending -= 1
    session.finishIfDone()
  }

  private fun onDisconnected(peripheral: CBPeripheral) {
    session(peripheral)?.result?.complete(GattResult.Failed)
  }

  private class CentralDelegate(private val owner: IosCentral) :
    NSObject(), CBCentralManagerDelegateProtocol {
    override fun centralManagerDidUpdateState(central: CBCentralManager) {
      owner._state.value = owner.currentState()
      owner.updateScan()
    }

    override fun centralManager(central: CBCentralManager, willRestoreState: Map<Any?, *>) {
      // The scan comes back with the manager; updateScan() restarts it once the radio is on.
      println("$TAG restored by iOS")
    }

    override fun centralManager(
      central: CBCentralManager,
      didDiscoverPeripheral: CBPeripheral,
      advertisementData: Map<Any?, *>,
      RSSI: NSNumber,
    ) = owner.onDiscover(didDiscoverPeripheral, advertisementData, RSSI.intValue)

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) =
      owner.onConnected(didConnectPeripheral)

    @ObjCSignatureOverride
    override fun centralManager(
      central: CBCentralManager,
      didFailToConnectPeripheral: CBPeripheral,
      error: NSError?,
    ) = owner.onDisconnected(didFailToConnectPeripheral)

    @ObjCSignatureOverride
    override fun centralManager(
      central: CBCentralManager,
      didDisconnectPeripheral: CBPeripheral,
      error: NSError?,
    ) = owner.onDisconnected(didDisconnectPeripheral)
  }

  private class PeripheralDelegate(private val owner: IosCentral) :
    NSObject(), CBPeripheralDelegateProtocol {
    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) =
      owner.onServices(peripheral, didDiscoverServices)

    @ObjCSignatureOverride
    override fun peripheral(
      peripheral: CBPeripheral,
      didDiscoverCharacteristicsForService: CBService,
      error: NSError?,
    ) = owner.onCharacteristics(peripheral, didDiscoverCharacteristicsForService)

    @ObjCSignatureOverride
    override fun peripheral(
      peripheral: CBPeripheral,
      didUpdateValueForCharacteristic: CBCharacteristic,
      error: NSError?,
    ) = owner.onValue(peripheral, didUpdateValueForCharacteristic, error)
  }

  private companion object {
    const val TAG = "CassetteScan"
    const val RESTORE_ID = "cassette.central"
    const val ARMED_KEY = "cassette.backgroundScan"
    val BATCH_WINDOW = 2.seconds
    val SRAM_SERVICE: CBUUID = CBUUID.UUIDWithString("FE51")
    val IDENTIFIER = Regex("^[0-9A-Fa-f]{8}-([0-9A-Fa-f]{4}-){3}[0-9A-Fa-f]{12}$")
    const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** Where the characteristics we read live, so a read discovers only those services. */
    val KNOWN_SERVICES: Map<String, String> = buildMap {
      val system = "d9050001-90aa-4c7c-b036-1e01fb8eb7ee"
      put("d9050002-90aa-4c7c-b036-1e01fb8eb7ee", system)
      put("d9050003-90aa-4c7c-b036-1e01fb8eb7ee", system)
      put("d9050004-90aa-4c7c-b036-1e01fb8eb7ee", system)
      put("d905000a-90aa-4c7c-b036-1e01fb8eb7ee", "d9050005-90aa-4c7c-b036-1e01fb8eb7ee")
      put(BATTERY_LEVEL_CHARACTERISTIC, "0000180f$BASE_SUFFIX")
    }

    /** Lower-case, full 128-bit: Core Bluetooth shortens standard UUIDs to "2A19". */
    fun normalise(uuid: String): String {
      val s = uuid.lowercase()
      return when (s.length) {
        4 -> "0000$s$BASE_SUFFIX"
        8 -> "$s$BASE_SUFFIX"
        else -> s
      }
    }

    fun cbuuid(uuid: String): CBUUID =
      CBUUID.UUIDWithString(
        if (uuid.endsWith(BASE_SUFFIX) && uuid.startsWith("0000")) uuid.substring(4, 8) else uuid
      )
  }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
  val size = length.toInt()
  if (size == 0) return ByteArray(0)
  return bytes!!.reinterpret<ByteVar>().readBytes(size)
}

private fun ByteArray.u16le(index: Int): Int =
  (this[index].toInt() and 0xFF) or ((this[index + 1].toInt() and 0xFF) shl 8)
