/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.jedis

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import spock.lang.Specification

/**
 * Tests that embedded Redis server is started correctly
 */
class JedisConfigSpec extends Specification {

  void setupSpec() {
    System.setProperty('org.slf4j.simpleLogger.defaultLogLevel', 'debug')
  }

  def 'should run embedded redis #description'() {
    given:
    Map properties = [:]
    properties.each { k, v -> System.setProperty(k, v) }

    and:
    def ctx = createContext()

    expect:
    ctx.getBean(JedisConfig).redisServer != null

    cleanup:
    ctx?.close()

    and:
    properties.each { k, v -> System.clearProperty(k) }
  }

  def 'should not run embedded redis #description'() {
    given:
    properties.each { k, v -> System.setProperty(k, v) }

    and:
    def ctx = createContext()

    expect:
    ctx.getBean(JedisConfig).redisServer == null

    cleanup:
    ctx?.close()

    and:
    properties.each { k, v -> System.clearProperty(k) }

    where:
    description                          | properties
    'if redis host is non-local'         | ['redis.host': '54.243.116.211']
    'if a connection string is provided' | ['redis.host': '127.0.0.1', 'redis.connection': 'redis://redistogo:8718a28b567e5676cb5a5cdca8d68365@grideye.redistogo.com:10912/']
  }

  private AnnotationConfigApplicationContext createContext() {
    def configs = []
    configs << JedisContext
    configs << JedisConfig
    new AnnotationConfigApplicationContext(configs as Class[])
  }

  @Configuration
  static class JedisContext {
    @Bean
    static PropertySourcesPlaceholderConfigurer ppc() {
      new PropertySourcesPlaceholderConfigurer()
    }
  }
}
