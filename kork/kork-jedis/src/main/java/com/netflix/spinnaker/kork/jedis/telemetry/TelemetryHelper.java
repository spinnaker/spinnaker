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
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryHelper {

  private static final String DEFAULT_ID_PREFIX = "redis.command";
  private static final String POOL_TAG = "poolName";

  private static Logger log = LoggerFactory.getLogger(TelemetryHelper.class);

  static Id timerId(Registry registry, String name, String command, boolean pipelined) {
    return registry
        .createId(DEFAULT_ID_PREFIX + ".latency." + command)
        .withTags(POOL_TAG, name, "pipelined", String.valueOf(pipelined));
  }

  static Id payloadSizeId(Registry registry, String name, String command, boolean pipelined) {
    return registry
        .createId(DEFAULT_ID_PREFIX + ".payloadSize." + command)
        .withTags(POOL_TAG, name, "pipelined", String.valueOf(pipelined));
  }

  static Id invocationId(
      Registry registry, String name, String command, boolean pipelined, boolean success) {
    return registry
        .createId(DEFAULT_ID_PREFIX + ".invocation." + command)
        .withTags(
            POOL_TAG,
            name,
            "pipelined",
            String.valueOf(pipelined),
            "success",
            String.valueOf(success));
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
    long size = 0;
    for (String p : payload) {
      size += payloadSize(p);
    }
    return size;
  }

  static long payloadSize(Map<String, String> payload) {
    long size = 0;
    for (Map.Entry<String, String> e : payload.entrySet()) {
      size += payloadSize(e.getKey());
      size += payloadSize(e.getValue());
    }
    return size;
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

  static long payloadSize(List payload) {
    long size = 0;
    for (Object p : payload) {
      if (p instanceof String) {
        size += payloadSize((String) p);
      } else {
        size += payloadSize((byte[]) p);
      }
    }
    return size;
  }
}
