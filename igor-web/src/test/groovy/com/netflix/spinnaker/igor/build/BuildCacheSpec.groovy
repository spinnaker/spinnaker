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

package com.netflix.spinnaker.igor.build

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BuildCacheSpec extends Specification {

    EmbeddedRedis embeddedRedis = EmbeddedRedis.embed()

    RedisClientDelegate redisClientDelegate = new JedisClientDelegate(embeddedRedis.pool as JedisPool)

    @Subject
    BuildCache cache = new BuildCache(redisClientDelegate, new IgorConfigurationProperties())

    final master = 'master'
    final test = 'test'
    final int TTL = 42

    void cleanup() {
        embeddedRedis.pool.resource.withCloseable { Jedis resource ->
            resource.flushDB()
        }
        embeddedRedis.destroy()
    }

    void 'new build numbers get overridden'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true, TTL)

        then:
        cache.getLastBuild(master, 'job1', true) == 78

        when:
        cache.setLastBuild(master, 'job1', 80, true, TTL)

        then:
        cache.getLastBuild(master, 'job1', true) == 80
    }

    void 'running and completed builds are handled separately'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true, TTL)

        then:
        cache.getLastBuild(master, 'job1', true) == 78

        when:
        cache.setLastBuild(master, 'job1', 80, false, TTL)

        then:
        cache.getLastBuild(master, 'job1', false) == 80
        cache.getLastBuild(master, 'job1', true) == 78
    }

    void 'when value is not found, -1 is returned'() {
        expect:
        cache.getLastBuild('notthere', 'job1', true) == -1
    }

    void 'can set builds for multiple masters'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true, TTL)
        cache.setLastBuild('example2', 'job1', 88, true, TTL)

        then:
        cache.getLastBuild(master, 'job1', true) == 78
        cache.getLastBuild('example2', 'job1', true) == 88
    }

    void 'correctly retrieves all jobsNames for a master'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true, TTL)
        cache.setLastBuild(master, 'job2', 11, false, TTL)
        cache.setLastBuild(master, 'blurb', 1, false, TTL)

        then:
        cache.getJobNames(master) == ['blurb', 'job2']
    }

    @Unroll
    void 'retrieves all matching jobs for typeahead #query'() {
        when:
        cache.setLastBuild(master, 'job1', 1, true, TTL)
        cache.setLastBuild(test, 'job1', 1, false, TTL)
        cache.setLastBuild(master, 'job2', 1, false, TTL)
        cache.setLastBuild(test, 'job3', 1, false, TTL)

        then:
        cache.getTypeaheadResults(query) == expected

        where:
        query  || expected
        'job'  || ['master:job1', 'master:job2', 'test:job1', 'test:job3']
        'job1' || ['master:job1', 'test:job1']
        'ob1'  || ['master:job1', 'test:job1']
        'B2'   || ['master:job2']
        '3'    || ['test:job3']
        'nope' || []
    }

    void 'a cache with another prefix does not pollute the current cache'() {
        given:
        def altCfg = new IgorConfigurationProperties()
        altCfg.spinnaker.jedis.prefix = 'newPrefix'
        BuildCache secondInstance = new BuildCache(redisClientDelegate, altCfg)

        when:
        secondInstance.setLastBuild(master, 'job1', 1, false, TTL)

        then:
        secondInstance.getJobNames(master) == ['job1']
        cache.getJobNames(master) == []

        when:
        embeddedRedis.pool.resource.withCloseable {
            it.del(cache.makeKey(master, 'job1'))
        }

        then:
        secondInstance.getJobNames(master) == ['job1']
    }

    void 'should generate nice keys for completed jobs'() {
        when:
        String key = cache.makeKey("travis-ci", "myorg/myrepo", false)

        then:
        key == "igor:builds:completed:travis-ci:MYORG/MYREPO:myorg/myrepo"
    }

    void 'should generate nice keys for running jobs'() {
        when:
        String key = cache.makeKey("travis-ci", "myorg/myrepo", true)

        then:
        key == "igor:builds:running:travis-ci:MYORG/MYREPO:myorg/myrepo"
    }

    void 'completed and running jobs should live in separate key space'() {
        when:
        def masterKey = 'travis-ci'
        def slug      = 'org/repo'

        then:
        cache.makeKey(masterKey, slug, false) != cache.makeKey(masterKey, slug, true)
    }
}
