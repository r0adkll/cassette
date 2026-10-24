package com.r0adkll.cassette.common.compose

/** Screen renders on the JVM show the Android app; they pass [SizeClasses.Apple] for iOS's. */
internal actual val platformSizeClasses: SizeClasses = SizeClasses.Material
