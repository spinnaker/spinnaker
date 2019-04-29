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
package com.netflix.spinnaker.kork.jedis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class JedisDriverProperties {

  /** The redis connection uri: (e.g. redis://localhost:6379) */
  public String connection;

  /** Client connection timeout. (Not to be confused with command timeout). */
  public int timeoutMs = 2000;

  /**
   * Redis object pool configuration.
   *
   * <p>If left null, the default object pool as defined in {@code JedisClientConfiguration} will be
   * used.
   */
  public GenericObjectPoolConfig poolConfig;
}
