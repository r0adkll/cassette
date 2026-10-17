package com.r0adkll.cassette.data.db

import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.time.Instant

/**
 * Row-to-domain mapping. Enums are stored by name; unknown names (from a newer build) degrade
 * safely.
 */
fun com.r0adkll.cassette.data.db.Bike.toDomain(): Bike =
    Bike(
        id = BikeId(id),
        name = name,
        type = enumOrNull<BikeType>(type) ?: BikeType.Other,
        photoPath = photo_path,
        hubSerial = hub_serial,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        lastSeenAt = last_seen_at?.let(Instant::fromEpochMilliseconds),
        hubReadAt = hub_read_at?.let(Instant::fromEpochMilliseconds),
    )

fun com.r0adkll.cassette.data.db.Component.toDomain(): Component =
    Component(
        id = ComponentId(id),
        bikeId = BikeId(bike_id),
        key = key,
        serial = serial,
        kind = enumOrNull<ComponentKind>(kind) ?: ComponentKind.UNKNOWN,
        modelId = model_id?.toInt(),
        deviceType = device_type?.toInt(),
        firmware = firmware,
        actuations = actuations,
        addedAt = Instant.fromEpochMilliseconds(added_at),
        lastSeenAt = last_seen_at?.let(Instant::fromEpochMilliseconds),
        removedAt = removed_at?.let(Instant::fromEpochMilliseconds),
        address = address,
    )

fun Battery_sample.toDomain(): BatteryReading =
    BatteryReading(
        componentId = ComponentId(component_id),
        at = Instant.fromEpochMilliseconds(at),
        millivolts = millivolts?.toInt(),
        percent = percent?.toInt(),
        reportedStatus = reported_status?.let { enumOrNull<BatteryStatus>(it) },
        method = enumOrNull<ReadMethod>(method) ?: ReadMethod.Advert,
    )

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
