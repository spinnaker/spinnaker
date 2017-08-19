package com.netflix.spinnaker.orca.kato.tasks.rollingpush

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedString
import spock.lang.Specification

class CleanUpTagsTaskSpec extends Specification {
  def "should create deleteEntityTags operations "() {
    given:
    def task = new CleanUpTagsTask()
    def stage = new Stage<>(new Pipeline(), "")
    stage.context = [
      application: "app",
      cloudProvider: "aws",
      source: [
        account: "test",
        asgName: "app-v00",
        region: "us-east-1"
      ],
      imageId: "imageId"
    ]

    and:
    def tags = [
      [
        tags: [
          [
            name: "tagName",
            value: [
              imageId: "imageId"
            ]
          ],
          [
            name: "tagName2",
            value: [
              imageId: "imageId1"
            ]
          ]
        ]
      ],
      [
        tags: [
          [
            name: "tagName3",
            value: [
              imageId: "imageId1"
            ]
          ],
          [
            name: "tagName3"
          ]
        ]
      ]
    ]

    def serverGroup = new TypedByteArray('application/json', new ObjectMapper().writeValueAsBytes([
      launchConfig: [
        imageId: "imageId"
      ]
    ]))

    Response oortResponse = new Response('http://oort', 200, 'OK', [], serverGroup);

    List<Map> operations = []
    task.objectMapper = new ObjectMapper();
    task.oortService = Mock(OortService) {
      1* getServerGroupFromCluster("app","test", "app", "app-v00", "us-east-1", "aws") >> {
        oortResponse
      }

      1* getEntityTags("aws", "servergroup", "app-v00", "test", "us-east-1") >> {
        tags
      }

      0 * _
    }

    task.katoService = Mock(KatoService) {
      1 * requestOperations('aws', _) >> {
        operations += it[1]
        rx.Observable.from(new TaskId(UUID.randomUUID().toString()))
      }
    }

    task.sourceResolver = new SourceResolver()

    when:
    task.execute(stage)

    then: "should only delete tags that have an imageId & if it doesn't match the imageId in the stage context"
    operations.size() == 1
    operations[0].deleteEntityTags.tags.size() == 2
    operations[0].deleteEntityTags.tags == ["tagName2", "tagName3"]
  }
}
