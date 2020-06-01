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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class TitusAmazonServerGroupCreatorDecoratorSpec extends Specification {
  def "should find image id from properties file"() {
    given:
    JenkinsTrigger jenkinsTrigger = new JenkinsTrigger("master", "job", 1, null)
    jenkinsTrigger.properties.put("imageName", "imageFromProperties")

    def pipeline = pipeline {
      trigger = jenkinsTrigger
      stage {
        type = "deploy"
      }
    }

    def stage = pipeline.stages[0]
    TitusAmazonServerGroupCreatorDecorator decorator = new TitusAmazonServerGroupCreatorDecorator()

    when:
    decorator.modifyCreateServerGroupOperationContext(stage, stage.context)

    then:
    stage.context.imageId == jenkinsTrigger.properties.get("imageName")

    when: 'trigger parameter specified'
    stage.context.remove("imageId")
    jenkinsTrigger.parameters.put("imageName", "imageFromParameters")
    decorator.modifyCreateServerGroupOperationContext(stage, stage.context)

    then: 'it takes precedence over props file'
    stage.context.imageId == jenkinsTrigger.parameters.get("imageName")
  }
}
