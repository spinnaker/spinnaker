package com.netflix.spinnaker.keel.api

import de.huxhorn.sulky.ulid.ULID

typealias UID = ULID.Value

private val idGenerator by lazy { ULID() }

fun randomUID(): UID = idGenerator.nextValue()

fun parseUID(ulid: String): UID = ULID.parseULID(ulid)
