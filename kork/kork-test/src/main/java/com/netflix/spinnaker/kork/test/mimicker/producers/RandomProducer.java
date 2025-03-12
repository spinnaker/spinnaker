/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.test.mimicker.producers;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;

public class RandomProducer {

  private final SecureRandom random;

  public RandomProducer(SecureRandom random) {
    this.random = random;
  }

  public boolean trueOrFalse() {
    return random.nextBoolean();
  }

  @NotNull
  public <T> T element(@NotNull List<T> list) {
    if (list.isEmpty()) {
      throw new IllegalArgumentException("list must have at least one value");
    }
    return list.get(random.nextInt(list.size()));
  }

  @NotNull
  public String alpha(int boundsMin, int boundsMax) {
    return RandomStringUtils.randomAlphabetic(boundsMin, boundsMax);
  }

  @NotNull
  public String alphanumeric(int boundsMin, int boundsMax) {
    return RandomStringUtils.randomAlphanumeric(boundsMin, boundsMax);
  }

  @NotNull
  public String alphanumeric(int length) {
    return RandomStringUtils.randomAlphanumeric(length);
  }

  @NotNull
  public String numeric(int length) {
    return RandomStringUtils.randomNumeric(length);
  }

  public int intValue(int boundsMin, int boundsMax) {
    return RandomUtils.nextInt(boundsMin, boundsMax);
  }

  public long longValue(long boundsMin, long boundsMax) {
    return RandomUtils.nextLong(boundsMin, boundsMax);
  }

  @NotNull
  public String uuid() {
    return UUID.randomUUID().toString();
  }
}
