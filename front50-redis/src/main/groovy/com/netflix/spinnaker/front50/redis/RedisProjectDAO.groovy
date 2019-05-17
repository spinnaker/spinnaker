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

import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions

class RedisProjectDAO implements ProjectDAO {

  static final String BOOK_KEEPING_KEY = 'com.netflix.spinnaker:front50:projects'

  RedisTemplate<String, Project> redisTemplate

  @Override
  Project findByName(String name) throws NotFoundException {
    def results = all().find { it.name == name }
    if (!results) {
      throw new NotFoundException("No Project found by name of ${name}")
    }
    return results
  }

  @Override
  Project findById(String id) throws NotFoundException {
    def results = redisTemplate.opsForHash().get(BOOK_KEEPING_KEY, id)
    if (!results) {
      throw new NotFoundException("No Project found by id of ${id}")
    }
    results
  }

  @Override
  Collection<Project> all() {
    return all(true)
  }

  @Override
  Collection<Project> all(boolean refresh) {
    redisTemplate.opsForHash()
      .scan(BOOK_KEEPING_KEY, ScanOptions.scanOptions().match('*').build())
      .withCloseable { Cursor<Map> c ->
      c.collect{ it.value }
    }
  }

  @Override
  Collection<Project> history(String id, int maxResults) {
    return [findById(id)]
  }

  @Override
  Project create(String id, Project item) {
    item.id = id ?: UUID.randomUUID().toString()

    if (!item.createTs) {
      item.createTs = System.currentTimeMillis()
    }

    redisTemplate.opsForHash().put(BOOK_KEEPING_KEY, item.id, item)

    item
  }

  @Override
  void update(String id, Project item) {
    item.updateTs = System.currentTimeMillis()
    create(id, item)
  }

  @Override
  void delete(String id) {
    redisTemplate.opsForHash().delete(BOOK_KEEPING_KEY, id)
  }

  @Override
  void bulkImport(Collection<Project> items) {
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
