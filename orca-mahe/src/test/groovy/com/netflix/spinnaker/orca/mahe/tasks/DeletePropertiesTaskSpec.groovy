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

package com.netflix.spinnaker.orca.mahe.tasks

import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.pipeline.CreatePropertyStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.IgnoreRest
import spock.lang.Specification

/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class DeletePropertiesTaskSpec extends Specification {

  MaheService maheService = Mock(MaheService)

  DeletePropertyTask task = new DeletePropertyTask(maheService: maheService)

  @IgnoreRest
  def "delete a single new persistent property"() {
    given:
    def pipeline = Execution.newPipeline('foo')
    def scope = [
      env: "test",
      appIdList: ["foo"],
      region: "us-west-1",
      stack: "main",
      cluster: "foo-main",
    ]

    def property = [key:"foo", value:'bar', constraints: 'none']

    def propertyIdList = ['foo|bar']

    def createPropertiesStage = new Stage(pipeline, CreatePropertyStage.PIPELINE_CONFIG_TYPE, [
      scope: scope,
      persistedProperties:[ property ],
      email: 'zthrash@netflix.com',
      cmcTicket: 'cmcTicket',
      propertyIdList: propertyIdList
    ])

    List captured

    when:
    def results = task.execute(createPropertiesStage)

    then:
    1 * maheService.deleteProperty(propertyIdList.first(), 'cmcTicket', scope.env) >> { res ->
      captured = res
      new Response("http://mahe", 200, "OK", [], new TypedString(propertyIdList.first()))
    }

    then:
    with(results.context) {
      deletedPropertyIdList.size() == 1
      deletedPropertyIdList.contains(propertyIdList.first())
    }
  }

}
