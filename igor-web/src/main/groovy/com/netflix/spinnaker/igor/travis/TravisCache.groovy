/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

import java.util.concurrent.TimeUnit


/*
    This creates an internal queue for igor triggered travis jobs.
    The travis api does not return an id we can track in the queue.
 */
@Service
@ConditionalOnProperty("travis.enabled")
class TravisCache {
    @Autowired
    JedisPool jedisPool

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    private String getPrefix() {
        return igorConfigurationProperties.spinnaker.jedis.prefix
    }

    String id = 'travis:builds:queue'

    Map getQueuedJob(String master, int queueNumber) {
        Jedis resource = jedisPool.getResource()

        resource.withCloseable {
            Map result = resource.hgetAll(makeKey(master, queueNumber))
            if (!result) {
                return [:]
            }
            Map convertedResult = [
                buildNumber: Integer.parseInt(result.buildNumber),
                jobName: result.jobName
            ]
            return convertedResult
        }
    }

    int setQueuedJob(String master, String jobName, int buildNumber) {
        Jedis resource = jedisPool.getResource()
        int queueId = (int) (long) TimeUnit.MILLISECONDS.toSeconds(new Date().getTime())
        resource.withCloseable {
            while (resource.exists(makeKey(master, queueId))) {
                queueId++;
            }
            resource.hset(makeKey(master, queueId), 'buildNumber', buildNumber as String)
            resource.hset(makeKey(master, queueId), 'jobName', jobName)
            return queueId
        }
    }

    void remove(String master, int queueId) {
        Jedis resource = jedisPool.getResource()
        resource.withCloseable {
            resource.del(makeKey(master, queueId))
        }
    }

    private String makeKey(String master, int queueId) {
        "${baseKey()}:${master}:${queueId}"
    }

    private String baseKey() {
        return "${prefix}:${id}"
    }
}
