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

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

// TODO rz - Ultra simple implementation at this point. Will eventually create a clouddriver engine if we need that
// level of load (but prob not).
object OrcaSimulationEngine extends App {

  // TODO rz - Add ~/.spinnaker/gatling.conf for setting up SSL
  val props = new GatlingPropertiesBuilder
  props.simulationClass(classOf[OrcaSimulation].getName)
  props.resultsDirectory("build/reports/gatling")
  props.binariesDirectory("build/classes/main")
  props.bodiesDirectory(getClass.getClassLoader.getResource("request-bodies").getPath)
  props.dataDirectory(getClass.getClassLoader.getResource("data").getPath)

  Gatling.fromMap(props.build)
  sys.exit()
}
