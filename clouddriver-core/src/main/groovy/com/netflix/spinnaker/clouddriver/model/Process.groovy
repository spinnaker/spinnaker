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
 * A Process is a terminating task, which on termination reports either "Success", or "Failure". It is run and managed
 * by a Cloud Provider, inside that Cloud Provider's deployment context. For example, in the case of the Kubernetes
 * Provider, a Process is a single container running to completion inside the Kubernetes Cluster.
 */
interface Process {
  String getName()

  String getId()

  String getLocation()

  String getJobId()

  HealthState getHealthState()

  List<String> getLoadBalancers()
}