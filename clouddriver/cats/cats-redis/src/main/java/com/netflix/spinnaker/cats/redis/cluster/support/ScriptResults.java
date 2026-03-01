/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.cats.redis.cluster.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight parsers for Redis Lua script results.
 *
 * <p>Responsibilities: - Parse structured results from EVAL/EVALSHA operations into simple DTOs
 *
 * <p>Non-responsibilities: - Orchestrating Lua script invocation (use RedisScriptManager) -
 * Time/cadence handling (use CadenceGuard/RedisTimeUtils)
 */
@Slf4j
public final class ScriptResults {
  private ScriptResults() {}

  /** Result holder for batch removal operations. */
  @Getter
  public static final class BatchRemovalResult {
    private final int removedCount;
    private final List<String> members;

    /**
     * Constructs a batch removal result.
     *
     * @param removedCount number of items removed (clamped to non-negative)
     * @param members list of removed member names (null-safe, defensively copied)
     */
    public BatchRemovalResult(int removedCount, List<String> members) {
      this.removedCount = Math.max(0, removedCount);
      this.members = members == null ? Collections.emptyList() : List.copyOf(members);
    }
  }

  /**
   * Parses the result of removeAgentsConditional script which returns [count, [member1, member2,
   * ...]].
   *
   * @param result raw Lua script result
   * @return parsed batch removal result
   */
  public static BatchRemovalResult parseRemoveAgentsConditional(Object result) {
    int count = 0;
    List<String> cleaned = new ArrayList<>();

    if (result instanceof List<?>) {
      List<?> list = (List<?>) result;
      if (list.size() < 2) {
        log.warn(
            "Unexpected Lua script result format: expected [count, list], got size: {}",
            list.size());
      }
      if (list.size() >= 1) {
        Object countObj = list.get(0);
        if (countObj instanceof Number) {
          count = ((Number) countObj).intValue();
        } else {
          log.warn(
              "Unexpected count element type in Lua result: {}",
              countObj != null ? countObj.getClass().getSimpleName() : "null");
        }
      }
      if (list.size() >= 2) {
        Object membersObj = list.get(1);
        if (membersObj instanceof List<?>) {
          for (Object elem : (List<?>) membersObj) {
            if (elem instanceof String) {
              cleaned.add((String) elem);
            }
          }
        } else {
          log.warn(
              "Unexpected second element type in Lua result: {}",
              membersObj != null ? membersObj.getClass().getSimpleName() : "null");
        }
      }
    } else {
      log.warn(
          "Unexpected Lua script result type: expected List, got: {}",
          result != null ? result.getClass().getSimpleName() : "null");
    }

    return new BatchRemovalResult(count, cleaned);
  }

  /**
   * Parses the count returned by addAgents script which typically returns [count, ...].
   *
   * @param result raw Lua script result
   * @return number of agents added (0 if parsing fails)
   */
  public static int parseAddAgentsCount(Object result) {
    try {
      if (result instanceof java.util.List) {
        java.util.List<?> list = (java.util.List<?>) result;
        if (!list.isEmpty()) {
          Object c0 = list.get(0);
          if (c0 instanceof Number) {
            return ((Number) c0).intValue();
          } else if (c0 instanceof String) {
            try {
              return Integer.parseInt((String) c0);
            } catch (Exception ignore) {
            }
          } else if (c0 instanceof byte[]) {
            try {
              return Integer.parseInt(
                  new String((byte[]) c0, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignore) {
            }
          }
        }
      }
    } catch (Exception ignore) {
    }
    return 0;
  }
}
