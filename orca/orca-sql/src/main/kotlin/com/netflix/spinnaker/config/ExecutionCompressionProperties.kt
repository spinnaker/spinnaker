/*
 * Copyright 2021 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.config

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Defines properties to be used when compressing large execution bodies
 * These properties apply when execution bodies are being upserted for
 * 1. Orchestration bodies
 * 2. Orchestration stage bodies
 * 3. Pipeline bodies
 * 4. Pipeline Stage bodies
 */
@ConfigurationProperties("execution-repository.sql.compression")
class ExecutionCompressionProperties {

  /**
   * Enables execution body compression for large stage and pipeline execution bodies
   */
  var enabled: Boolean = false

  /**
   * Determines whether writing compressed bodies is enabled, or only reading.
   * Only relevant when enabled is true.
   */
  var compressionMode: CompressionMode = CompressionMode.READ_WRITE;

  /**
   * Defines the body size threshold, in bytes, above which the body will be compressed before
   * upsertion
   */
  var bodyCompressionThreshold: Int = 1024

  /**
   * Controls the library to be used when compressing bodies
   */
  var compressionType: CompressionType = CompressionType.ZLIB

  fun isWriteEnabled() = enabled && (compressionMode == CompressionMode.READ_WRITE)
}

/**
 * Enum defining the support compression types
 */
enum class CompressionType(val type: String) {
  GZIP("GZIP"),
  ZLIB("ZLIB");

  fun getDeflator(outStream: OutputStream) =
    when (this) {
      GZIP -> GZIPOutputStream(outStream)
      ZLIB -> DeflaterOutputStream(outStream)
    }

  fun getInflator(inStream: InputStream) =
    when (this) {
      GZIP -> GZIPInputStream(inStream)
      ZLIB -> InflaterInputStream(inStream)
    }
}

enum class CompressionMode {
     READ_WRITE,
     READ_ONLY;
}
