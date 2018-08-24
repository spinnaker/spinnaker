/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.model

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import java.util.*

/**
 * Holds binary data with some kind of type wart.
 */
data class TypedByteArray(
  val type: String,
  val data: ByteArray
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TypedByteArray

    if (type != other.type) return false
    if (!Arrays.equals(data, other.data)) return false

    return true
  }

  override fun hashCode(): Int =
    31 * type.hashCode() + Arrays.hashCode(data)
}

/**
 * Common interface for assets and partial assets.
 */
interface AssetBase {
  val id: AssetId
  val apiVersion: String
  val kind: String
  val spec: TypedByteArray
}

/**
 * Internal representation of an asset.
 */
data class Asset(
  override val id: AssetId,
  override val apiVersion: String = "1.0",
  override val kind: String,
  val dependsOn: Set<AssetId> = emptySet(),
  override val spec: TypedByteArray
) : AssetBase {

  fun wrap(): AssetContainer = AssetContainer(this)
}

/**
 * Internal representation of a partial asset.
 */
data class PartialAsset(
  override val id: AssetId,
  val root: AssetId,
  override val apiVersion: String = "1.0",
  override val kind: String,
  override val spec: TypedByteArray
) : AssetBase {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PartialAsset

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

/**
 * Internal representation of an asset container.
 */
data class AssetContainer(
  val asset: Asset,
  val partialAssets: Set<PartialAsset> = setOf()
)

data class AssetId(
  val value: String
) {
  override fun toString(): String = value
}

val Asset.fingerprint: HashCode
  get() = Hashing.murmur3_128().hashBytes(spec.data)
