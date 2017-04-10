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

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

object OrcaSimulationConfig {

  def loadConfig(): Config = {
    val configFilePath = sys.props.get("simulation.config")

    if (configFilePath.isDefined) {
      val file = new File(configFilePath.get)
      ConfigFactory.parseFile(file)
    } else {
      ConfigFactory.parseResources("orca-simulation.conf")
    }
  }
}

class OrcaSimulationConfig(config: Config) {

  val serviceUrl = config.getString("service.orca.serviceUrl")

  val rampUpPeriod = config.getInt("service.orca.rampUpPeriod")
  val duration = config.getInt("service.orca.duration")

  val submitOrchestration = new {
    val rampUsersPerSec = config.getInt("service.orca.submitOrchestration.rampUsersPerSec")
    val rampUsersTo = config.getInt("service.orca.submitOrchestration.rampUsersTo")
  }
}
