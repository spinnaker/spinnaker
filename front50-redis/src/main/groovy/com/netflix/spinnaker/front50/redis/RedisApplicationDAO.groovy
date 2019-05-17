/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.redis

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
/**
 * Implementation of {@link ApplicationDAO} interface, leveraging {@link RedisTemplate} to do the
 * heavy lifting.
 */
class RedisApplicationDAO implements ApplicationDAO {
  private static final Logger log = LoggerFactory.getLogger(this)
  ObjectMapper objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

  static final String BOOK_KEEPING_KEY = 'com.netflix.spinnaker:front50:applications'

  RedisTemplate<String, Application> redisTemplate

  @Override
  Application findByName(String name) throws NotFoundException {
    findById(name)
  }

  @Override
  Collection<Application> search(Map<String, String> attributes) {
    ApplicationDAO.Searcher.search(all(), attributes)
  }

  @Override
  Application findById(String name) throws NotFoundException {
    def app = redisTemplate.opsForHash().get(BOOK_KEEPING_KEY, name.toUpperCase())
    if (!app) {
      throw new NotFoundException("No application found by id ${name}")
    }
    app
  }

  @Override
  Collection<Application> all() {
    return all(true)
  }

  @Override
  Collection<Application> all(boolean refresh) {
    def applications = redisTemplate.opsForHash()
      .scan(BOOK_KEEPING_KEY, ScanOptions.scanOptions().match('*').build())
      .withCloseable { Cursor<Map> c ->
        c.collect{ it.value }
      }

    if (!applications) {
      throw new NotFoundException("No applications available")
    }

    applications
  }

  @Override
  Collection<Application> history(String id, int maxResults) {
    return [findById(id)]
  }

  @Override
  Application create(String id, Application application) {
    if (!application.createTs) {
      application.createTs = System.currentTimeMillis() as String
    }
    application.name = id.toUpperCase()

    redisTemplate.opsForHash().put(BOOK_KEEPING_KEY, application.name, application)

    application
  }

  @Override
  void update(String id, Application application) {
    application.name = id
    application.updateTs = System.currentTimeMillis() as String

    create(id, application)
  }

  @Override
  void delete(String id) {
    redisTemplate.opsForHash().delete(BOOK_KEEPING_KEY, id.toUpperCase())
  }

  @Override
  void bulkImport(Collection<Application> items) {
    items.each { create(it.id, it) }
  }

  @Override
  boolean isHealthy() {
    try {
      redisTemplate.opsForHash().get("", "")
      return true
    } catch (Exception e) {
      return false
    }
  }
}
