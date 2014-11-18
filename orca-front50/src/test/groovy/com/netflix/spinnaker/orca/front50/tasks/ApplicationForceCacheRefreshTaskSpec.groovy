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

package com.netflix.spinnaker.orca.front50.tasks

import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class ApplicationForceCacheRefreshTaskSpec extends Specification {
  @Subject task = new ApplicationForceCacheRefreshTask()
  def config = [
    account: "fzlem"
  ]
  def stage = new Stage("forceRefresh", config)

  void "should force cache refresh applications via oort"() {
    setup:
    task.oort = Mock(OortService)

    when:
    task.execute(stage)

    then:
    1 * task.oort.forceCacheUpdate(ApplicationForceCacheRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.account == config.account
    }
  }
}
