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

    def tearDown() {
        jedis.close()
        embeddedRedis.destroy()
    }

    def "migrates all v1 keys to v2 format"() {
        given:
        def v1Keys = [
            new DockerRegistryV1Key("igor", "dockerRegistry", "acct", "registry", "netflix.com", "tag"),
            new DockerRegistryV1Key("igor", "dockerRegistry", "acct", "registry", "http://netflix.com", "tag"),
            new DockerRegistryV1Key("igor", "dockerRegistry", "acct", "registry", "netflix.com:1111", "tag"),
        ]
        def v2Keys = [
            new DockerRegistryV2Key("igor", "dockerRegistry", "acct", "registry", "tag"),
            new DockerRegistryV2Key("igor", "dockerRegistry", "acct", "registry", "tag")
        ]
        def invalidKeys = [
            new DockerRegistryV2Key("igor", "totalInvalid", "acct", "registry", "tag")

        ]
        def allKeys = [v1Keys, v2Keys, invalidKeys].flatten()
        allKeys.each { jedis.hmset(it.toString(), [foo: "bar"]) }

        expect:
        jedis.keys("igor:*").size() == 5
        jedis.keys("igor:*:v2:*").size() == v2Keys.size()

        when:
        subject.run()

        then:
        noExceptionThrown()
        [v1Keys.collect { keyFactory.convert(it) }, v2Keys].flatten().unique().each {
            assert jedis.exists(it.toString()), "${it} was not created"
            assert jedis.hgetAll(it.toString()) == [foo: "bar"], "${it} did not get correct contents"
        }
        [v1Keys.each {
            assert jedis.ttl(it.toString()) != null, "${it} did not get an expiration"
        }]
    }
}
