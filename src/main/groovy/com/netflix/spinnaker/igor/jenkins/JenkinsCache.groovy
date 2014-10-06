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

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisCommands

/**
 * Shared cache of Jenkins details
 */
@SuppressWarnings(['PropertyName', 'DuplicateNumberLiteral'])
@Service
@Slf4j
class JenkinsCache {

    @Autowired
    JedisCommands jedis

    @Value('${redis.prefix:igor}')
    String prefix

    List<String> getJobNames(String master) {
        jedis.keys("${prefix}:${master}:*").collect { extractJobName(it) }.sort()
    }

    List<String> getTypeaheadResults(String search) {
        jedis.keys("${prefix}:*:*${search.toUpperCase()}*:*").collect { extractTypeaheadResult(it) }.sort()
    }

    Map getLastBuild(String master, String job) {
        if (!jedis.exists(makeKey(master, job))) {
            return [:]
        }
        Map result = jedis.hgetAll(makeKey(master, job))
        [
            lastBuildLabel : result.lastBuildLabel as Integer,
            lastBuildStatus: result.lastBuildStatus
        ]
    }

    void setLastBuild(String master, String job, int lastBuild, String status) {
        String key = makeKey(master, job)
        jedis.hset(key, 'lastBuildLabel', lastBuild as String)
        jedis.hset(key, 'lastBuildStatus', status)
    }

    void remove(String master, String job) {
        jedis.del(makeKey(master, job))
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
