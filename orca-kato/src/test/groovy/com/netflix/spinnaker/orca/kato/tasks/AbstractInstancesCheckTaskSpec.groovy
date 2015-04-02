/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.orca.kato.tasks
import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedInput

class AbstractInstancesCheckTaskSpec extends Specification {

  // The standard Spock Spy behavior was breaking here.
  static interface HasSucceededSpy {
    boolean hasSucceeded(Map asg, List instances, Collection<String> interestingHealthProviderNames)
  }

  static class TestInstancesCheckTask extends AbstractInstancesCheckTask {
    HasSucceededSpy hasSucceededSpy

    @Override
    protected Map<String, List<String>> getServerGroups(Stage stage) {
      return [
          'us-west-1': ['front50-v000']
      ]
    }

    @Override
    protected boolean hasSucceeded(Stage stage, Map asg, List<Map> instances, Collection<String> interestingHealthProviderNames) {
      hasSucceededSpy.hasSucceeded(asg, instances, interestingHealthProviderNames)
    }
  }

  @Subject task = new TestInstancesCheckTask()

  Closure<Response> contsructResponse = { int status, String body ->
    new Response("", status, "", [], new TypedInput() {
      @Override
      String mimeType() {
        return null
      }

      @Override
      long length() {
        return 0
      }

      @Override
      InputStream "in"() throws IOException {
        new ByteArrayInputStream(body.getBytes());
      }
    })
  }

  void "should be provided with health provider names"() {
    task.oortService = Mock(OortService)
    task.objectMapper = new OrcaObjectMapper()
    task.hasSucceededSpy = Mock(HasSucceededSpy)

    def pipeline = new Pipeline()
    pipeline.appConfig.interestingHealthProviderNames = ["JustTrustMeBroItIsHealthy"]
    def stage = new PipelineStage(pipeline, "whatever", [
      "account.name"                  : "test",
      "targetop.asg.enableAsg.name"   : "front50-v000",
      "targetop.asg.enableAsg.regions": ["us-west-1"]
    ])

    when:
    task.execute(stage.asImmutable())

    then:
    1 * task.oortService.getCluster("front50", "test", "front50", "aws") >> contsructResponse(200, '''
{
    "serverGroups": [
        {
            "name": "front50-v000",
            "region": "us-west-1",
            "asg": {
                "minSize": 1
            },
            "instances": [
                {
                    "name": "i-12345678"
                }
            ]
        }
    ]
}
''')

    and:
    1 * task.hasSucceededSpy.hasSucceeded(_, [['name':'i-12345678']], ['JustTrustMeBroItIsHealthy'])
  }
}
