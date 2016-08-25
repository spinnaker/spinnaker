/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.model

/**
 * A Job is a collection of Processes. The Job defines how many processes may run, and the failure/success conditions
 * leading to a terminal status.
 *
 * See Process.groovy for an overview of Processes.
 */
interface Job {
  String getName()

  String getCluster()

  String getAccount()

  String getId()

  String getLocation()

  String getProvider()

  JobState getJobState()

  Instance getInstance()

  Long getCreatedTime()

  Long getFinishTime()
}
