package com.netflix.spinnaker.keel.core.api

import de.huxhorn.sulky.ulid.ULID
import java.time.Instant

typealias UID = ULID.Value

private val idGenerator by lazy { ULID() }

fun randomUID(): UID = idGenerator.nextValue()

fun parseUID(ulid: String): UID = ULID.parseULID(ulid)

fun UID.timestampAsInstant() = Instant.ofEpochMilli(timestamp())
