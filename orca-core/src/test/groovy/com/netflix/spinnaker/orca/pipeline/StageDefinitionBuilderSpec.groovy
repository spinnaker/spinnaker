/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class StageDefinitionBuilderSpec extends Specification {
  def dynamicConfigService = Mock(DynamicConfigService)

  @Subject
  def stageDefinitionBuilder = new MyStageDefinitionBuilder()

  @Unroll
  def "should check dynamic config property"() {
    when:
    def isForceCacheRefreshEnabled = stageDefinitionBuilder.isForceCacheRefreshEnabled(dynamicConfigService)

    then:
    1 * dynamicConfigService.isEnabled("stages.my-stage-definition-builder.force-cache-refresh", true) >> {
      return response()
    }
    0 * _

    isForceCacheRefreshEnabled == expectedForceCacheRefreshEnabled

    where:
    response << [{ return true }, { return false }, { throw new NullPointerException() }]
    expectedForceCacheRefreshEnabled << [true, false, true]
  }

  private static class MyStageDefinitionBuilder implements StageDefinitionBuilder {

  }
}
