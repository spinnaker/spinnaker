/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.cats.compression;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GZipCompression implements CompressionStrategy {

  private static final Logger log = LoggerFactory.getLogger(GZipCompression.class);

  private static final String CHARSET = "UTF-8";

  private final long thresholdBytesSize;
  private final boolean enabled;

  public GZipCompression(long thresholdBytesSize, boolean enabled) {
    log.info(
        "Cats using gzip compression: {} bytes threshold, compress enabled: {}",
        thresholdBytesSize,
        enabled);
    this.thresholdBytesSize = thresholdBytesSize;
    this.enabled = enabled;
  }

  @Override
  public String compress(final String str) {
    if (str == null) {
      return null;
    }

    byte[] bytes;
    try {
      bytes = str.getBytes(CHARSET);
    } catch (UnsupportedEncodingException e) {
      log.error("Failed to compress string: {}", str, e);
      return str;
    }

    if (!enabled || bytes.length < thresholdBytesSize) {
      return str;
    }

    ByteArrayOutputStream obj = new ByteArrayOutputStream();
    try {
      GZIPOutputStream gzip = new GZIPOutputStream(obj);
      gzip.write(bytes);
      gzip.flush();
      gzip.close();
    } catch (IOException e) {
      log.error("Failed to compress string: {}", str, e);
      return str;
    }

    try {
      return new String(Base64.getEncoder().encode(obj.toByteArray()), CHARSET);
    } catch (UnsupportedEncodingException e) {
      log.error("Failed to compress string: {}", str, e);
      return str;
    }
  }

  @Override
  public String decompress(final String compressed) {
    if (compressed == null) {
      return null;
    }

    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(compressed.getBytes(CHARSET));
    } catch (IllegalArgumentException | UnsupportedEncodingException e) {
      return compressed;
    }

    if (bytes.length == 0 || !isCompressed(bytes)) {
      return compressed;
    }

    final StringBuilder outStr = new StringBuilder();
    try {
      final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, CHARSET));

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        outStr.append(line);
      }
    } catch (IOException e) {
      log.error("Failed to decompress string: {}", compressed, e);
      return compressed;
    }

    return outStr.toString();
  }

  private static boolean isCompressed(final byte[] compressed) {
    return compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)
        && compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8);
  }
}
