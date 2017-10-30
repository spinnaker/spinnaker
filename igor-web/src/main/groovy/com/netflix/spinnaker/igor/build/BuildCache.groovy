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
package com.netflix.spinnaker.igor.build

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool


/**
 * Shared cache of build details
 */
@SuppressWarnings(['PropertyName', 'DuplicateNumberLiteral'])
@Service
class BuildCache {

    @Autowired
    JedisPool jedisPool

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    String id = 'builds'

    List<String> getJobNames(String master) {
        Jedis resource = jedisPool.resource
        List<String> jobs = resource.keys("${baseKey()}:completed:${master}:*").collect { extractJobName(it) }
        jobs.addAll(resource.keys("${baseKey()}:running:${master}:*").collect { extractJobName(it) })

        jedisPool.returnResource(resource)
        jobs.sort().unique()
    }

    List<String> getTypeaheadResults(String search) {
        Jedis resource = jedisPool.resource
        List<String> results = resource.keys("${baseKey()}:*:*:*${search.toUpperCase()}*:*").collect {
            extractTypeaheadResult(it)
        }.sort()
        jedisPool.returnResource(resource)
        results
    }

    int getLastBuild(String master, String job, boolean running) {
        Jedis resource = jedisPool.resource
        def key = makeKey(master, job, running)
        if (!resource.exists(key)) {
            jedisPool.returnResource(resource)
            return -1
        }
        int buildNumber = resource.get(key) as Integer

        jedisPool.returnResource(resource)
        buildNumber
    }

    Long getTTL(String master, String job) {
        Jedis resource = jedisPool.resource
        Long ttl = resource.ttl(makeKey(master, job))
        jedisPool.returnResource(resource)
        return ttl
    }

    void setTTL(String key, int ttl) {
        Jedis resource = jedisPool.resource
        resource.expire(key, ttl)
        jedisPool.returnResource(resource)
    }

    void setLastBuild(String master, String job, int lastBuild, boolean building, int ttl) {
        if(!building) {
            // This is here to support rollback to igor versions
            setBuild(makeKey(master, job), lastBuild, building, master, job, ttl)
        }
        storeLastBuild(makeKey(master, job, building), lastBuild, ttl)
    }

    List<String> getDeprecatedJobNames(String master) {
        Jedis resource = jedisPool.resource
        List<String> jobs = resource.keys("${baseKey()}:${master}:*").collect { extractDeprecatedJobName(it) }.sort()
        jedisPool.returnResource(resource)
        jobs
    }

    Map getDeprecatedLastBuild(String master, String job) {
        Jedis resource = jedisPool.resource
        if (!resource.exists(makeKey(master, job))) {
            jedisPool.returnResource(resource)
            return [:]
        }
        Map result = resource.hgetAll(makeKey(master, job))
        Map convertedResult = [
            lastBuildLabel: Integer.parseInt(result.lastBuildLabel),
            lastBuildBuilding: Boolean.parseBoolean(result.lastBuildBuilding)
        ]
        jedisPool.returnResource(resource)
        convertedResult
    }



    private void setBuild(String key, int lastBuild, boolean building, String master, String job, int ttl) {
        Jedis resource = jedisPool.resource
        resource.hset(key, 'lastBuildLabel', lastBuild as String)
        resource.hset(key, 'lastBuildBuilding', building as String)
        jedisPool.returnResource(resource)
        setTTL(key, ttl)
    }

    private void storeLastBuild(String key, int lastBuild, int ttl) {
        Jedis resource = jedisPool.resource
        resource.set(key, lastBuild as String)
        jedisPool.returnResource(resource)
        setTTL(key, ttl)
    }

    protected String makeKey(String master, String job) {
        "${baseKey()}:${master}:${job.toUpperCase()}:${job}"
    }

    protected String makeKey(String master, String job, boolean running) {
        def buildState = running ? "running" : "completed"
        "${baseKey()}:${buildState}:${master}:${job.toUpperCase()}:${job}"
    }

    private static String extractJobName(String key) {
        key.split(':')[5]
    }

    private static String extractDeprecatedJobName(String key) {
        key.split(':')[4]
    }

    private static String extractTypeaheadResult(String key) {
        def parts = key.split(':')
        "${parts[3]}:${parts[5]}"
    }

    private String baseKey() {
        return "${igorConfigurationProperties.spinnaker.jedis.prefix}:${id}"
    }
}
