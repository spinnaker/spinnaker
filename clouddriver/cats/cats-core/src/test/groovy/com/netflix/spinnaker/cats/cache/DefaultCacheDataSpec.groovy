/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.cats.cache

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DefaultCacheDataSpec extends Specification {
  def "should set cacheExpiry attribute based on ttlSeconds"() {
    given:
    def cacheData = new DefaultCacheData("id", 100, [:], [:], Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

    expect:
    cacheData.ttlSeconds == 100
    cacheData.attributes.cacheExpiry == 100 * 1000
  }

  def "should set ttlSeconds attribute based on cacheExpiry"() {
    given:
    def cacheData = new DefaultCacheData("id", -1, [cacheExpiry: 150L * 1000], [:], Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

    expect:
    cacheData.ttlSeconds == 150
    cacheData.attributes.cacheExpiry == 150 * 1000
  }
}
