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

package com.netflix.spinnaker.kork.jedis

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.JedisPool
import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests that embedded Redis server is started correctly
 */
class JedisConfigSpec extends Specification {

    JedisPool pool = Mock(JedisPool)
    Jedis jedis = Mock(Jedis)

    @Subject
    JedisCommands commands = java.lang.reflect.Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), [JedisCommands] as Class[], new JedisConfig.JedisDelegatingMethodInvocationHandler(pool))


    def 'should get return resource on successful operation'() {
        when:
        commands.exists('foo')

        then:
        1 * pool.getResource() >> jedis
        1 * jedis.exists('foo') >> true
        1 * pool.returnResource(jedis)
    }

    def 'should return broken resource on failure'() {
        when:
        commands.exists('foo')

        then:
        1 * pool.getResource() >> jedis
        1 * jedis.exists('foo') >> { throw new IllegalArgumentException('foo') }
        1 * pool.returnBrokenResource(jedis)
        thrown IllegalArgumentException
    }
}
