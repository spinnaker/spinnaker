/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.docker

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import rx.schedulers.Schedulers
import spock.lang.Specification
import spock.lang.Subject

class DockerRegistryCacheV2KeysMigrationSpec extends Specification {

    private static final V1_KEYS = [
        new DockerRegistryV1Key("igor", "dockerRegistry", "acct", "registry", "netflix.com", "tag"),
        new DockerRegistryV1Key("igor", "dockerRegistry", "acct", "registry", "http://netflix.com", "tag"),
        new DockerRegistryV1Key("igor", "dockerRegistry", "acct", "registry", "netflix.com:1111", "tag"),
    ]
    private static final V2_KEYS = [
        new DockerRegistryV2Key("igor", "dockerRegistry", "acct", "registry", "tag"),
        new DockerRegistryV2Key("igor", "dockerRegistry", "acct", "registry", "tag")
    ]
    private static final INVALID_KEYS = [
        new DockerRegistryV2Key("igor", "totalInvalid", "acct", "registry", "tag")
    ]
    private static final ALL_KEYS = [V1_KEYS, V2_KEYS, INVALID_KEYS].flatten()

    EmbeddedRedis embeddedRedis
    Jedis jedis
    RedisClientDelegate redisClientDelegate
    IgorConfigurationProperties igorConfigurationProperties = new IgorConfigurationProperties()
    DockerRegistryKeyFactory keyFactory = new DockerRegistryKeyFactory(igorConfigurationProperties)

    @Subject
    DockerRegistryCacheV2KeysMigration subject

    def setup() {
        embeddedRedis = EmbeddedRedis.embed()
        jedis = embeddedRedis.jedis
        redisClientDelegate = new JedisClientDelegate(embeddedRedis.pool as JedisPool)
        subject = new DockerRegistryCacheV2KeysMigration(
            redisClientDelegate,
            keyFactory, igorConfigurationProperties, Schedulers.immediate())
    }

    void cleanup() {
        embeddedRedis.pool.resource.withCloseable { Jedis resource ->
            resource.flushDB()
        }
        jedis.close()
        embeddedRedis.destroy()
    }

    def "migrates all v1 keys to v2 format"() {
        given:
        ALL_KEYS.each { jedis.hmset(it.toString(), [foo: "bar"]) }

        expect:
        jedis.keys("igor:*").size() == 5
        jedis.keys("igor:*:v2:*").size() == V2_KEYS.size()

        when:
        subject.run()

        then:
        noExceptionThrown()
        [V1_KEYS.collect { keyFactory.convert(it) }, V2_KEYS].flatten().unique().each {
            assert jedis.exists(it.toString()), "${it} was not created"
            assert jedis.hgetAll(it.toString()) == [foo: "bar"], "${it} did not get correct contents"
        }
        [V1_KEYS.each {
            assert jedis.ttl(it.toString()) != null, "${it} did not get an expiration"
        }]
    }

    def "should not migrate v2 keys twice"() {
        given:
        ALL_KEYS.each { jedis.hmset(it.toString(), [foo: "bar"]) }

        when:
        subject.run()

        then:
        noExceptionThrown()
        assert jedis.keys("igor:*:v2:*") == [
            "igor:totalInvalid:v2:acct:registry:tag",
            "igor:dockerRegistry:v2:acct:registry:tag"
        ] as Set
    }
}
