/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Transaction

class RedisBackedBakeStore implements BakeStore {

  @Autowired
  ObjectMapper mapper

  private JedisPool jedisPool;

  public RedisBackedBakeStore(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public void storeBakeStatus(String bakeKey, String region, BakeRequest bakeRequest, BakeStatus bakeStatus) {
    def bakeRequestJson = mapper.writeValueAsString(bakeRequest)
    def bakeStatusJson = mapper.writeValueAsString(bakeStatus)
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      Transaction t = jedis.multi()

      t.zadd("allBakes", System.currentTimeMillis(), bakeKey)

      t.hmset(bakeStatus.id, [
        bakeKey    : bakeKey,
        region     : region,
        bakeRequest: bakeRequestJson,
        bakeStatus : bakeStatusJson
      ])

      t.del(bakeKey)

      t.hmset(bakeKey, [
        id         : bakeStatus.id,
        region     : region,
        bakeRequest: bakeRequestJson,
        bakeStatus : bakeStatusJson
      ])

      t.sadd("allBakes:incomplete", bakeStatus.id)

      t.exec()
    }
  }

  @Override
  public void updateBakeDetails(Bake bakeDetails) {
    def bakeDetailsJson = mapper.writeValueAsString(bakeDetails)
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeKey = jedis.hget(bakeDetails.id, "bakeKey")

      Transaction t = jedis.multi()

      t.hset(bakeDetails.id, "bakeDetails", bakeDetailsJson)
      t.hset(bakeKey, "bakeDetails", bakeDetailsJson)

      t.exec()
    }
  }

  @Override
  public void updateBakeStatus(BakeStatus bakeStatus, Map<String, String> logsContent=[:]) {
    def bakeStatusJson = mapper.writeValueAsString(bakeStatus)
    def bakeLogsJson = mapper.writeValueAsString(logsContent ?: [:])
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeKey = jedis.hget(bakeStatus.id, "bakeKey")

      Transaction t = jedis.multi()

      t.hmset(bakeStatus.id, [
        bakeStatus: bakeStatusJson,
        bakeLogs  : bakeLogsJson
      ])

      if (bakeKey) {
        t.hmset(bakeKey, [
          bakeStatus: bakeStatusJson,
          bakeLogs  : bakeLogsJson
        ])
      }

      if (bakeStatus.state != BakeStatus.State.PENDING && bakeStatus.state != BakeStatus.State.RUNNING) {
        t.srem("allBakes:incomplete", bakeStatus.id)
      }

      t.exec()
    }
  }

  @Override
  public void storeBakeError(String bakeId, String error) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeKey = jedis.hget(bakeId, "bakeKey")

      Transaction t = jedis.multi()

      t.hset(bakeId, "bakeError", error)

      if (bakeKey) {
        t.hset(bakeKey, "bakeError", error)
      }

      t.exec()
    }
  }

  @Override
  public String retrieveRegionById(String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      return jedis.hget(bakeId, "region")
    }
  }

  @Override
  public BakeStatus retrieveBakeStatusByKey(String bakeKey) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeStatusJson = jedis.hget(bakeKey, "bakeStatus")

      return bakeStatusJson ? mapper.readValue(bakeStatusJson, BakeStatus) : null
    }
  }

  @Override
  public BakeStatus retrieveBakeStatusById(String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeStatusJson = jedis.hget(bakeId, "bakeStatus")

      return bakeStatusJson ? mapper.readValue(bakeStatusJson, BakeStatus) : null
    }
  }

  @Override
  public Bake retrieveBakeDetailsById(String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeDetailsJson = jedis.hget(bakeId, "bakeDetails")

      return bakeDetailsJson ? mapper.readValue(bakeDetailsJson, Bake) : null
    }
  }

  @Override
  public Map<String, String> retrieveBakeLogsById(String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeLogsJson = jedis.hget(bakeId, "bakeLogs")

      return bakeLogsJson ? mapper.readValue(bakeLogsJson, Map) : null
    }
  }

  @Override
  public boolean deleteBakeByKey(String bakeKey) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeId = jedis.hget(bakeKey, "id")

      Transaction t = jedis.multi()

      t.zrem("allBakes", bakeKey)
      t.del(bakeKey)

      if (bakeId) {
        t.srem("allBakes:incomplete", bakeId)
        t.del(bakeId)
      }

      return t.exec()[0] == 1
    }
  }

  @Override
  public boolean cancelBakeById(String bakeId) {
    def bakeStatus = new BakeStatus(id: bakeId,
                                    resource_id: bakeId,
                                    state: BakeStatus.State.CANCELLED,
                                    result: BakeStatus.Result.FAILURE)
    def bakeStatusJson = mapper.writeValueAsString(bakeStatus)
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeKey = jedis.hget(bakeId, "bakeKey")

      Transaction t = jedis.multi()

      t.srem("allBakes:incomplete", bakeId)
      t.hset(bakeStatus.id, "bakeStatus", bakeStatusJson)

      if (bakeKey) {
        t.zrem("allBakes", bakeKey)
        t.del(bakeKey)
      }

      return t.exec()[0] == 1
    }
  }

  @Override
  public Set<String> getIncompleteBakeIds() {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      return jedis.smembers("allBakes:incomplete")
    }
  }

}
