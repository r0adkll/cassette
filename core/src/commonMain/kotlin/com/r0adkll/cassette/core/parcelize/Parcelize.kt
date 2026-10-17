package com.r0adkll.cassette.core.parcelize

/** `@Parcelize` for shared code; the `cassette.parcelize` plugin wires it up on Android. */
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.BINARY) annotation class Parcelize

expect interface Parcelable
