/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.jedis.telemetry;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class TelemetryHelper {

  private final static String DEFAULT_ID_PREFIX = "redis.command";
  private final static String POOL_TAG = "poolName";

  private static Logger log = LoggerFactory.getLogger(TelemetryHelper.class);

  static Id timerId(Registry registry, String name, String command) {
    return registry.createId(DEFAULT_ID_PREFIX + ".latency." + command).withTag(POOL_TAG, name);
  }

  static Id payloadSizeId(Registry registry, String name, String command) {
    return registry.createId(DEFAULT_ID_PREFIX + ".payloadSize." + command).withTag(POOL_TAG, name);
  }

  static Id errorId(Registry registry, String name, String command) {
    return registry.createId(DEFAULT_ID_PREFIX + ".error." + command).withTag(POOL_TAG, name);
  }

  static Id allErrorId(Registry registry, String name) {
    return registry.createId(DEFAULT_ID_PREFIX + ".error").withTag(POOL_TAG, name);
  }

  static long payloadSize(String payload) {
    try {
      return payload.getBytes("UTF-8").length;
    } catch (UnsupportedEncodingException e) {
      log.error("could not get payload size, setting to -1", e);
      return -1;
    }
  }

  static long payloadSize(String... payload) {
    StringBuilder b = new StringBuilder();
    for (String p : payload) {
      b.append(p);
    }
    return payloadSize(b.toString());
  }

  static long payloadSize(Map<String, String> payload) {
    StringBuilder b = new StringBuilder();
    for (Map.Entry<String, String> e : payload.entrySet()) {
      b.append(e.getKey());
      b.append(e.getValue());
    }
    return payloadSize(b.toString());
  }

  static long payloadSize(byte[] payload) {
    return payload.length;
  }

  static long payloadSize(byte[]... payload) {
    long size = 0;
    for (byte[] p : payload) {
      size += p.length;
    }
    return size;
  }
}
