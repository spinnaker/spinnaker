/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.repository.dao

import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
class JedisJobInstanceDaoSpec extends Specification {

  def pool = new JedisPool(new JedisPoolConfig(), "localhost")
  @AutoCleanup def jedis = pool.resource
  @Subject dao = new JedisJobInstanceDao(jedis)

  def "getJobInstance by name and parameters"() {
    given:
    dao.createJobInstance("foo", new JobParameters(a: new JobParameter("a")))

    expect:
    dao.getJobInstance(name, parameters) == null ^ shouldBeFound

    where:
    name  | parameterMap     | shouldBeFound
    "foo" | [a: "a"]         | true
    "foo" | [a: "b"]         | false
    "foo" | [b: "a"]         | false
    "bar" | [a: "a"]         | false
    "foo" | [a: "a", b: "b"] | false

    parameters = new JobParameters(parameterMap.collectEntries {
      [(it.key): new JobParameter(it.value)]
    })
  }

}
