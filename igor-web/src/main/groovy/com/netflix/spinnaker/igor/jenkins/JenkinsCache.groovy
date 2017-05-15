/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.jenkins

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

/**
 * Shared jenkinsCache of build details for jenkins
 */
@SuppressWarnings(['PropertyName', 'DuplicateNumberLiteral'])
@Service
class JenkinsCache {
    @Autowired
    JedisPool jedisPool

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    static final String POLL_STAMP = "lastPollCycleTimestamp"

    private String getPrefix() {
        igorConfigurationProperties.spinnaker.jedis.prefix
    }

    List<String> getJobNames(String master) {
        jedisPool.resource.withCloseable { Jedis resource  ->
            return resource.keys("${prefix}:${master}:*").collect { extractJobName(it) }.sort()

        }
    }

    List<String> getTypeaheadResults(String search) {
        jedisPool.resource.withCloseable { Jedis resource ->
            return resource.keys("${prefix}:*:*${search.toUpperCase()}*:*").collect {
                extractTypeaheadResult(it)
            }.sort()
        }
    }

    Map getLastBuild(String master, String job) {
        jedisPool.resource.withCloseable { Jedis resource ->
            if (!resource.exists(makeKey(master, job))) {
                return [:]
            }
            Map result = resource.hgetAll(makeKey(master, job))
            Map convertedResult = [
                lastBuildLabel: Integer.parseInt(result.lastBuildLabel),
                lastBuildBuilding: Boolean.parseBoolean(result.lastBuildBuilding)
            ]
            return convertedResult
        }
    }

    void setLastBuild(String master, String job, int lastBuild, boolean building) {
        jedisPool.resource.withCloseable { Jedis resource ->
            String key = makeKey(master, job)
            resource.hset(key, 'lastBuildLabel', lastBuild as String)
            resource.hset(key, 'lastBuildBuilding', building as String)
        }
    }

    void setLastPollCycleTimestamp(String master, String job, Long timestamp) {
        jedisPool.resource.withCloseable { Jedis resource ->
            String key = makeKey(master, job)
            resource.hset(key, POLL_STAMP, timestamp as String)
        }
    }

    Long getLastPollCycleTimestamp(String master, String job) {
        jedisPool.resource.withCloseable { Jedis resource ->
            String result = resource.hget(makeKey(master, job), POLL_STAMP)
            return result as Long
        }
    }

    Boolean getEventPosted(String master, String job, Long cursor, Integer buildNumber) {
        jedisPool.resource.withCloseable { Jedis resource ->
            String key = makeKey(master, job) + ":${POLL_STAMP}:${cursor}"
            String result = resource.hget(key, buildNumber as String)
            return result
        }
    }

    void setEventPosted(String master, String job, Long cursor, Integer buildNumber) {
        jedisPool.resource.withCloseable { Jedis resource ->
            String key = makeKey(master, job) + ":${POLL_STAMP}:${cursor}"
            resource.hset(key, buildNumber as String, "POSTED")
        }
    }

    void pruneOldMarkers(String master, String job, Long cursor) {
        remove(master, job)
        jedisPool.resource.withCloseable { Jedis resource ->
            String key = makeKey(master, job) + ":${POLL_STAMP}:${cursor}"
            resource.del(key)
        }
    }

    void remove(String master, String job) {
        jedisPool.resource.withCloseable { Jedis resource ->
            resource.del(makeKey(master, job))
        }
    }

    private String makeKey(String master, String job) {
        "${prefix}:${master}:${job.toUpperCase()}:${job}"
    }

    private static String extractJobName(String key) {
        key.split(':')[3]
    }

    private static String extractTypeaheadResult(String key) {
        def parts = key.split(':')
        "${parts[1]}:${parts[3]}"
    }

}
