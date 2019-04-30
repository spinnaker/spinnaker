/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation

import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CloudFormationForceCacheRefreshTaskSpec extends Specification {
  @Subject task = new CloudFormationForceCacheRefreshTask()

  void "should force cache refresh cloud formations via mort"() {
    given:
    def stage = stage()
    task.cacheService = Mock(CloudDriverCacheService)

    when:
    task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', CloudFormationForceCacheRefreshTask.REFRESH_TYPE, Collections.emptyMap())
  }

  @Unroll
  void "should add scoping data if available"() {
    given:      
    task.cacheService = Mock(CloudDriverCacheService)
    
    and:
    def stage = stage()
    stage.context.put("credentials", credentials)
    stage.context.put("regions", regions)

    when:
    task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', CloudFormationForceCacheRefreshTask.REFRESH_TYPE, expectedData)

    where:
    credentials   | regions       || expectedData
    null          | null          || [:]
    "credentials" | null          || [credentials: "credentials"]
    null          | ["eu-west-1"] || [region: ["eu-west-1"]]
    "credentials" | ["eu-west-1"] || [credentials: "credentials", region: ["eu-west-1"]]


  }
}
