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

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CloudFormationForceCacheRefreshTaskSpec extends Specification {
  @Subject task = new CloudFormationForceCacheRefreshTask()
  def stage = stage()

  void "should force cache refresh cloud formations via mort"() {
    setup:
    task.cacheService = Mock(CloudDriverCacheService)

    when:
    task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', CloudFormationForceCacheRefreshTask.REFRESH_TYPE, Collections.emptyMap())
  }
}
