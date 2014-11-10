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

package com.netflix.spinnaker.orca.pipeline.jedis

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.pipeline.Pipeline
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class JedisPipelineStoreSpec extends Specification {

  @Shared @AutoCleanup("destroy") EmbeddedRedis embeddedRedis
  @Shared mapper = new ObjectMapper()

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.flushDB()
  }

  def jedis = embeddedRedis.jedisCommands
  @Subject pipelineStore = new JedisPipelineStore(jedis, mapper)

  def "a pipeline is written to the redis store"() {
    given:
    def pipeline = Pipeline.builder()
                           .withApplication("orca")
                           .withName("dummy-pipeline")
                           .withStages("one", "two", "three")
                           .build()

    expect:
    pipeline.id == null

    when:
    pipelineStore.store(pipeline)

    then:
    pipeline.id != null

    and:
    def key = "pipeline:$pipeline.id"
    jedis.exists(key)
    with(mapper.readValue(jedis.hgetAll(key).config, Map)) {
      application == pipeline.application
      name == pipeline.name
      stages.type == pipeline.stages.type
    }
  }

}
