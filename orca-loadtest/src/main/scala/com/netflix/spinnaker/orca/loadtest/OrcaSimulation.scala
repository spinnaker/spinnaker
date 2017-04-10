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
package com.netflix.spinnaker.orca.loadtest

import com.netflix.spinnaker.orca.loadtest.scenarios.OrcaScenarios
import io.gatling.core.Predef._
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class OrcaSimulation extends Simulation {

  val config = new OrcaSimulationConfig(OrcaSimulationConfig.loadConfig())

  // TODO rz - Make a circular feeder of RawFileBody instead. This might take a bit more digging.
  lazy val CircularTaskFeeder: Feeder[String] = {
    Iterator.continually(Map(
      "body" -> ""
    ))
  }

  setUp {
    createScenarioList()
  }

  def createScenarioList(): List[PopulationBuilder] = {
    val scenarios: ListBuffer[PopulationBuilder] = new ListBuffer()

    if (config.submitOrchestration.rampUsersTo > 0) {
      scenarios.append(
        OrcaScenarios.submitOrchestration(CircularTaskFeeder).inject(
          rampUsersPerSec(config.submitOrchestration.rampUsersPerSec) to config.submitOrchestration.rampUsersTo during config.rampUpPeriod.seconds,
          constantUsersPerSec(config.submitOrchestration.rampUsersTo) during config.duration
        ).protocols(http.baseURL(config.serviceUrl))
      )
    }

    scenarios.toList
  }
}
