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
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.jobs.BakeRecipe
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisDataException

import java.util.concurrent.TimeUnit

class RedisBackedBakeStore implements BakeStore {

  public static final String INCOMPLETE_BAKES_PREFIX = "allBakes:incomplete:"

  @Autowired
  String roscoInstanceId

  private ObjectMapper mapper = new ObjectMapper()
  private JedisPool jedisPool
  private RedisClientDelegate redisClientDelegate

  def scriptNameToSHAMap = [:]

  public RedisBackedBakeStore(JedisPool jedisPool, RedisClientDelegate redisClientDelegate) {
    this.jedisPool = jedisPool;
    this.redisClientDelegate = redisClientDelegate;
  }

  private void cacheAllScripts() {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      scriptNameToSHAMap.with {
        // Expected key list: lock key, bake key
        // Expected arg list: ttlMilliseconds
        acquireBakeLockSHA = jedis.scriptLoad("""\
          -- Set the lock key key if it's not already set.
          if redis.call('SETNX', KEYS[1], 'locked') == 1 then
            -- Set TTL of 5 seconds.
            redis.call('PEXPIRE', KEYS[1], ARGV[1])

            -- Delete the bake key key.
            redis.call('DEL', KEYS[2])

            -- We acquired the lock.
            return true
          else
            -- We failed to acquire the lock.
            return false
          end
        """)
        // Expected key list: "allBakes", bake id, bake key, this instance incomplete bakes key, lock key
        // Expected arg list: createdTimestampMilliseconds, region, bake request json, bake status json, bake logs json, command, rosco instance id
        storeNewBakeStatusSHA = jedis.scriptLoad("""\
          -- Delete the bake id key.
          redis.call('DEL', KEYS[2])

          -- Add bake key to set of bakes.
          redis.call('ZADD', KEYS[1], ARGV[1], KEYS[3])

          -- If we lost a race to initiate a new bake, just return the race winner's bake status.
          if redis.call('EXISTS', KEYS[3]) == 1 then
            return redis.call('HMGET', KEYS[3], 'bakeStatus')
          end

          -- Set bake id hash values.
          redis.call('HMSET', KEYS[2],
                     'bakeKey', KEYS[3],
                     'region', ARGV[2],
                     'bakeRecipe', ARGV[3],
                     'bakeRequest', ARGV[4],
                     'bakeStatus', ARGV[5],
                     'bakeLogs', ARGV[6],
                     'command', ARGV[7],
                     'roscoInstanceId', ARGV[8],
                     'createdTimestamp', ARGV[1],
                     'updatedTimestamp', ARGV[1])

          -- Set bake key hash values.
          redis.call('HMSET', KEYS[3],
                     'id', KEYS[2],
                     'region', ARGV[2],
                     'bakeRecipe', ARGV[3],
                     'bakeRequest', ARGV[4],
                     'bakeStatus', ARGV[5],
                     'bakeLogs', ARGV[6],
                     'command', ARGV[7],
                     'roscoInstanceId', ARGV[8],
                     'createdTimestamp', ARGV[1],
                     'updatedTimestamp', ARGV[1])

          -- Add bake id to set of incomplete bakes.
          redis.call('SADD', KEYS[4], KEYS[2])

          -- Delete the lock key key instead of just allowing it to wait out the TTL.
          redis.call('DEL', KEYS[5])
        """)
        // Expected key list: bake id
        // Expected arg list: bake details json
        updateBakeDetailsSHA = jedis.scriptLoad("""\
          local existing_bake_status = redis.call('HGET', KEYS[1], 'bakeStatus')

          -- Ensure we don't update/resurrect a canceled bake (can happen due to a race).
          if existing_bake_status and cjson.decode(existing_bake_status)['state'] == '$BakeStatus.State.CANCELED' then
            return
          end

          -- Retrieve the bake key associated with bake id.
          local bake_key = redis.call('HGET', KEYS[1], 'bakeKey')

          -- Update the bake details set on the bake id hash.
          redis.call('HSET', KEYS[1], 'bakeDetails', ARGV[1])

          -- Update the bake details set on the bake key hash.
          redis.call('HSET', bake_key, 'bakeDetails', ARGV[1])
        """)
        // Expected key list: bake id, this instance incomplete bakes key
        // Expected arg list: bake status json, bake logs json, updatedTimestampMilliseconds
        def updateBakeStatusBaseScript = """\
          local existing_bake_status = redis.call('HGET', KEYS[1], 'bakeStatus')

          -- Ensure we don't update/resurrect a canceled bake (can happen due to a race).
          if existing_bake_status and cjson.decode(existing_bake_status)['state'] == '$BakeStatus.State.CANCELED' then
            return
          end

          -- Retrieve the bake key associated with bake id.
          local bake_key = redis.call('HGET', KEYS[1], 'bakeKey')

          -- Update the bake status and logs set on the bake id hash.
          redis.call('HMSET', KEYS[1],
                     'bakeStatus', ARGV[1],
                     'bakeLogs', ARGV[2],
                     'updatedTimestamp', ARGV[3])

          if bake_key then
            -- Update the bake status and logs set on the bake key hash.
            redis.call('HMSET', bake_key,
                       'bakeStatus', ARGV[1],
                       'bakeLogs', ARGV[2],
                       'updatedTimestamp', ARGV[3])
          end
        """
        updateBakeStatusSHA = jedis.scriptLoad(updateBakeStatusBaseScript)
        // Expected key list: bake id, this instance incomplete bakes key
        // Expected arg list: bake status json, bake logs json, updatedTimestampMilliseconds
        def updateBakeStatusWithIncompleteRemovalScript = updateBakeStatusBaseScript + """\
          -- Remove bake id from set of incomplete bakes.
          redis.call('SREM', KEYS[2], KEYS[1])
        """
        updateBakeStatusWithIncompleteRemovalSHA = jedis.scriptLoad(updateBakeStatusWithIncompleteRemovalScript)
        // Expected key list: bake id
        // Expected arg list: error
        storeBakeErrorSHA = jedis.scriptLoad("""\
          -- Retrieve the bake key associated with bake id.
          local bake_key = redis.call('HGET', KEYS[1], 'bakeKey')

          -- Update the error set on the bake id hash.
          redis.call('HSET', KEYS[1], 'bakeError', ARGV[1])

          if bake_key then
            -- Update the error set on the bake key hash.
            redis.call('HSET', bake_key, 'bakeError', ARGV[1])
          end
        """)
        // Expected key list: bake key, "allBakes", incomplete bake keys...
        // Expected arg list:
        deleteBakeByKeySHA = jedis.scriptLoad("""\
          -- Retrieve the bake id associated with bake key.
          local bake_id = redis.call('HGET', KEYS[1], 'id')

          -- Remove bake key from the set of bakes.
          redis.call('ZREM', KEYS[2], KEYS[1])

          -- Delete the bake key key.
          redis.call('DEL', KEYS[1])

          if bake_id then
            -- Retrieve the rosco instance id associated with bake id.
            local rosco_instance_id = redis.call('HGET', bake_id, 'roscoInstanceId')

            if rosco_instance_id then
              -- Remove bake id from that rosco instance's set of incomplete bakes.
              redis.call('SREM', 'allBakes:incomplete:' .. rosco_instance_id, bake_id)
            end

            -- Delete the bake id key.
            redis.call('DEL', bake_id)
          end

          return bake_id
        """)
        // Expected key list: bake key, "allBakes", incomplete bake keys...
        // Expected arg list: updatedTimestampMilliseconds
        deleteBakeByKeyPreserveDetailsSHA = jedis.scriptLoad("""\
          -- Retrieve the bake id associated with bake key.
          local bake_id = redis.call('HGET', KEYS[1], 'id')

          -- Remove bake key from the set of bakes.
          redis.call('ZREM', KEYS[2], KEYS[1])

          -- Delete the bake key key.
          redis.call('DEL', KEYS[1])

          if bake_id then
            -- Retrieve the rosco instance id associated with bake id.
            local rosco_instance_id = redis.call('HGET', bake_id, 'roscoInstanceId')

            if rosco_instance_id then
              -- Remove bake id from that rosco instance's set of incomplete bakes.
              redis.call('SREM', 'allBakes:incomplete:' .. rosco_instance_id, bake_id)
            end

            -- Set bake state to CANCELED if still running.
            local bake_status = redis.call('HGET', bake_id, 'bakeStatus')

            if bake_status then
              local bake_status_json = cjson.decode(bake_status)

              if bake_status_json['state'] == '$BakeStatus.State.RUNNING' then
                bake_status_json['state'] = '$BakeStatus.State.CANCELED'
                bake_status_json['result'] = '$BakeStatus.Result.FAILURE'

                -- Update the bake status set on the bake id hash.
                 redis.call('HMSET', bake_id,
                            'bakeStatus', cjson.encode(bake_status_json),
                            'updatedTimestamp', ARGV[1])
              end
            end
          end

          return bake_id
        """)
        // Expected key list: bake id, "allBakes", incomplete bake keys...
        // Expected arg list: bake status json, updatedTimestampMilliseconds
        cancelBakeByIdSHA = jedis.scriptLoad("""
          -- Retrieve the bake key associated with bake id.
          local bake_key = redis.call('HGET', KEYS[1], 'bakeKey')

          -- Retrieve the rosco instance id associated with bake id.
          local rosco_instance_id = redis.call('HGET', KEYS[1], 'roscoInstanceId')

          local ret

          if rosco_instance_id then
            -- Remove bake id from that rosco instance's set of incomplete bakes.
            ret = redis.call('SREM', 'allBakes:incomplete:' .. rosco_instance_id, KEYS[1])
          end

          -- Update the bake status set on the bake id hash.
          redis.call('HMSET', KEYS[1],
                     'bakeStatus', ARGV[1],
                     'updatedTimestamp', ARGV[2])

          if bake_key then
            -- Remove the bake key from the set of bakes.
            redis.call('ZREM', KEYS[2], bake_key)

            -- Delete the bake key key.
            redis.call('DEL', bake_key)
          end

          return ret
        """)
      }
    }
  }

  private String getAllIncompleteBakesKeyPattern() {
    return "$INCOMPLETE_BAKES_PREFIX*"
  }

  private String getThisInstanceIncompleteBakesKey() {
    return "$INCOMPLETE_BAKES_PREFIX$roscoInstanceId".toString()
  }

  @Override
  public boolean acquireBakeLock(String bakeKey) {
    def lockKey = "lock:$bakeKey"
    def ttlMilliseconds = "5000"
    def keyList = [lockKey.toString(), bakeKey]
    def argList = [ttlMilliseconds]

    return evalSHA("acquireBakeLockSHA", keyList, argList)
  }

  @Override
  public BakeStatus storeNewBakeStatus(String bakeKey, String region, BakeRecipe bakeRecipe, BakeRequest bakeRequest, BakeStatus bakeStatus, String command) {
    def lockKey = "lock:$bakeKey"
    def bakeRecipeJson = mapper.writeValueAsString(bakeRecipe)
    def bakeRequestJson = mapper.writeValueAsString(bakeRequest)
    def bakeStatusJson = mapper.writeValueAsString(bakeStatus)
    def bakeLogsJson = mapper.writeValueAsString(bakeStatus.logsContent ? [logsContent: bakeStatus.logsContent] : [:])
    def createdTimestampMilliseconds = timeInMilliseconds
    def keyList = ["allBakes", bakeStatus.id, bakeKey, thisInstanceIncompleteBakesKey, lockKey.toString()]
    def argList = [createdTimestampMilliseconds as String, region, bakeRecipeJson, bakeRequestJson, bakeStatusJson, bakeLogsJson, command, roscoInstanceId]
    def result = evalSHA("storeNewBakeStatusSHA", keyList, argList)

    // Check if the script returned a bake status set by the winner of a race.
    if (result?.getAt(0)) {
      bakeStatus = mapper.readValue(result[0], BakeStatus)
    }

    return bakeStatus
  }

  @Override
  public void updateBakeDetails(Bake bakeDetails) {
    def bakeDetailsJson = mapper.writeValueAsString(bakeDetails)
    def keyList = [bakeDetails.id]
    def argList = [bakeDetailsJson]

    saveImageToBakeRelationship(bakeDetails.artifact.getLocation(), bakeDetails.artifact.getReference(), bakeDetails.id)

    evalSHA("updateBakeDetailsSHA", keyList, argList)
  }

  @Override
  public void updateBakeStatus(BakeStatus bakeStatus) {
    def bakeStatusJson = mapper.writeValueAsString(bakeStatus)
    def bakeLogsJson = mapper.writeValueAsString(bakeStatus.logsContent ? [logsContent: bakeStatus.logsContent] : [:])
    def updatedTimestampMilliseconds = timeInMilliseconds
    def scriptSHA = "updateBakeStatusSHA"

    if (bakeStatus.state != BakeStatus.State.RUNNING) {
      scriptSHA = "updateBakeStatusWithIncompleteRemovalSHA"
    }

    def keyList = [bakeStatus.id, thisInstanceIncompleteBakesKey]
    def argList = [bakeStatusJson, bakeLogsJson, updatedTimestampMilliseconds + ""]

    evalSHA(scriptSHA, keyList, argList)
  }

  @Override
  public void storeBakeError(String bakeId, String error) {
    def keyList = [bakeId]
    def argList = [error]

    evalSHA("storeBakeErrorSHA", keyList, argList)
  }

  @Override
  public String retrieveRegionById(String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      return jedis.hget(bakeId, "region")
    }
  }

  @Override
  public String retrieveCloudProviderById(String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeKey = jedis.hget(bakeId, "bakeKey")

      // Bake key is always bake:$cloudProvider:...
      return bakeKey?.split(":")?.getAt(1)
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
      def (String bakeStatusJson,
           String createdTimestampStr,
           String updatedTimestampStr) = jedis.hmget(bakeId, "bakeStatus", "createdTimestamp", "updatedTimestamp")

      BakeStatus bakeStatus = bakeStatusJson ? mapper.readValue(bakeStatusJson, BakeStatus) : null

      if (bakeStatus && createdTimestampStr) {
        bakeStatus.createdTimestamp = Long.parseLong(createdTimestampStr)
        bakeStatus.updatedTimestamp = Long.parseLong(updatedTimestampStr)
      }

      return bakeStatus
    }
  }

  @Override
  public BakeRequest retrieveBakeRequestById(String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def bakeRequestJson = jedis.hget(bakeId, "bakeRequest")
      return bakeRequestJson ? mapper.readValue(bakeRequestJson, BakeRequest) : null
    }
  }

  @Override
  public BakeRecipe retrieveBakeRecipeById(String bakeId) {
    def jedis = jedisPool.getResource()
    jedis.withCloseable {
      def bakeRecipeJson = jedis.hget(bakeId, "bakeRecipe")
      return bakeRecipeJson ? mapper.readValue(bakeRecipeJson, BakeRecipe) : null
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
  public String deleteBakeByKey(String bakeKey) {
    def keyList = [bakeKey, "allBakes"]
    keyList += scanIncompleteBakesKeys()

    return evalSHA("deleteBakeByKeySHA", keyList, [])
  }

  @Override
  public String deleteBakeByKeyPreserveDetails(String bakeKey) {
    def updatedTimestampMilliseconds = timeInMilliseconds
    def keyList = [bakeKey, "allBakes"]
    keyList += scanIncompleteBakesKeys()

    def argList = [updatedTimestampMilliseconds + ""]

    return evalSHA("deleteBakeByKeyPreserveDetailsSHA", keyList, argList)
  }

  @Override
  public boolean cancelBakeById(String bakeId) {
    def bakeStatus = new BakeStatus(id: bakeId,
                                    resource_id: bakeId,
                                    state: BakeStatus.State.CANCELED,
                                    result: BakeStatus.Result.FAILURE)
    def bakeStatusJson = mapper.writeValueAsString(bakeStatus)
    def updatedTimestampMilliseconds = timeInMilliseconds
    def keyList = [bakeId, "allBakes"]
    keyList += scanIncompleteBakesKeys()

    def argList = [bakeStatusJson, updatedTimestampMilliseconds + ""]

    return evalSHA("cancelBakeByIdSHA", keyList, argList) == 1
  }

  @Override
  public void removeFromIncompletes(String roscoInstanceId, String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      jedis.srem("$INCOMPLETE_BAKES_PREFIX$roscoInstanceId", bakeId)
    }
  }

  @Override
  public Set<String> getThisInstanceIncompleteBakeIds() {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      return jedis.smembers(thisInstanceIncompleteBakesKey)
    }
  }

  @Override
  public Map<String, Set<String>> getAllIncompleteBakeIds() {
    Set<String> incompleteBakesKeys = scanIncompleteBakesKeys()
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      return incompleteBakesKeys.collectEntries { incompleteBakesKey ->
        String roscoInstanceId = incompleteBakesKey.substring(INCOMPLETE_BAKES_PREFIX.length())

        [(roscoInstanceId): jedis.smembers(incompleteBakesKey)]
      }
    }
  }

  @Override
  public void saveImageToBakeRelationship(String region, String image, String bakeId) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      jedis.set("${region}:${image}", bakeId)
    }
  }

  @Override
  String getBakeIdFromImage(String region, String image) {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      return jedis.get("${region}:${image}")
    }
  }

  @Override
  public long getTimeInMilliseconds() {
    def jedis = jedisPool.getResource()

    jedis.withCloseable {
      def (String timeSecondsStr, String microsecondsStr) = jedis.time()
      long timeSeconds = Long.parseLong(timeSecondsStr)
      long microseconds = Long.parseLong(microsecondsStr)

      return TimeUnit.SECONDS.toMillis(timeSeconds) + TimeUnit.MICROSECONDS.toMillis(microseconds)
    }
  }

  @CompileStatic
  private Set<String> scanIncompleteBakesKeys() {
    def incompleteBakesKeys = new HashSet()
    redisClientDelegate.withKeyScan(allIncompleteBakesKeyPattern, 1000, { page ->
      incompleteBakesKeys.addAll(page.getResults())
    })
    return incompleteBakesKeys
  }

  @CompileStatic
  private Object evalSHA(String scriptName, List<String> keyList, List<String> argList) {
    String scriptSHA = scriptNameToSHAMap[scriptName]

    try {
      if (!scriptSHA) {
        cacheAllScripts()

        scriptSHA = scriptNameToSHAMap[scriptName]
      }

      def jedis = jedisPool.getResource()

      jedis.withCloseable {
        return jedis.evalsha((String)scriptSHA, keyList, argList)
      }
    } catch (JedisDataException e) {
      // If the redis server doesn't recognize the SHA1 hash, cache the scripts.
      if (e.message?.startsWith("NOSCRIPT")) {
        cacheAllScripts()

        def jedis = jedisPool.getResource()

        jedis.withCloseable {
          return jedis.evalsha((String)scriptSHA, keyList, argList)
        }
      } else {
        throw e
      }
    }
  }
}
