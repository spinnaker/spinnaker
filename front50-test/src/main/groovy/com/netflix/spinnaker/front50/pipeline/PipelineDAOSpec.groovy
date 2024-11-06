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

  def "filters pipelines by pipeline name"() {
    when:
    instance.create("0", new Pipeline(application: "foo", name: "pipelineName1"))
    for (int i = 1; i < 10; i++) {
      def name = i % 2 == 0 ? "pipelineNameA" + i : "pipelineNameB" + i;
      instance.create(i.toString(), new Pipeline(application: "foo", name: name))
    }
    def filteredPipelines = instance.getPipelinesByApplication("foo", "NameA", true);

    then:
    // the pipelines are not guaranteed to be in order of insertion
    pipelinesContainName(filteredPipelines, "pipelineNameA2")
    pipelinesContainName(filteredPipelines, "pipelineNameA4")
    pipelinesContainName(filteredPipelines, "pipelineNameA6")
    pipelinesContainName(filteredPipelines, "pipelineNameA8")
    filteredPipelines.size() == 4
  }

  def "filters pipelines by pipeline name case insensitive"() {
    when:
    instance.create("0", new Pipeline(application: "foo", name: "PipElinenamea"))
    def filteredPipelines = instance.getPipelinesByApplication("foo", "NameA", true);

    then:
    filteredPipelines[0].getName() == "PipElinenamea"
    filteredPipelines.size() == 1
  }

  private static boolean pipelinesContainName(Collection<Pipeline> pipelines, String name) {
    return pipelines.stream().filter {pipeline -> pipeline.getName() == name}.findAny().isPresent();
  }

}
