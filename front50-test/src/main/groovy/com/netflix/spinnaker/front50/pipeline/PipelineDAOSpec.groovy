/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.pipeline


import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import spock.lang.*

abstract class PipelineDAOSpec<T extends PipelineDAO> extends Specification {

  abstract T getInstance()

  def "cannot create a pipeline without an application"() {
    when:
    instance.create("1", new Pipeline(name: "I have no application"))

    then:
    thrown IllegalArgumentException
  }

  def "cannot create a pipeline without a name"() {
    when:
    instance.create("1", new Pipeline(application: "foo"))

    then:
    thrown IllegalArgumentException
  }

}
