/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.api

import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobStageProperties
import com.netflix.spinnaker.orca.api.preconfigured.jobs.TitusPreconfiguredJobProperties
import spock.lang.Specification

class TitusPreconfiguredJobPropertiesSpec extends Specification {

  def 'should default values for cluster config'() {
    when:
    PreconfiguredJobStageProperties props = new TitusPreconfiguredJobProperties("label", "Atype")
    props.cluster.application = "test"
    props.cluster.imageId = 'something:latest'
    props.cluster.region = 'us-east-1'

    then:
    props.type == 'Atype'
    props.cloudProvider == 'titus'
    props.label == 'label'
    props.cluster.retries == 2
    def capacity = props.cluster.capacity
    capacity.desired == 1
    capacity.min == 1
    capacity.max == 1
    def resources = props.cluster.resources
    resources.cpu == 1
    resources.disk == 10000
    resources.gpu == 0
    resources.memory == 512
    resources.networkMbps == 128
    props.enabled
    props.waitForCompletion
    props.overridableFields
    !props.producesArtifacts
    props.isValid()
  }

}
