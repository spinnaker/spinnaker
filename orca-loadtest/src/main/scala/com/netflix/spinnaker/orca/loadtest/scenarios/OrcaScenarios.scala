/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.loadtest.scenarios

import com.netflix.spinnaker.orca.loadtest.actions.OrcaActions
import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder

object OrcaScenarios {

  def submitOrchestration(tasks: FeederBuilder[String]): ScenarioBuilder = {
    scenario("Submit orchestration")
      .feed(tasks)
      .exec(OrcaActions.postOrchestration)
  }
}

