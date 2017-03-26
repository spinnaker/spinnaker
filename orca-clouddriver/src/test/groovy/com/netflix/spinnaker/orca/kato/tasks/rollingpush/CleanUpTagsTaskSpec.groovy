package com.netflix.spinnaker.orca.kato.tasks.rollingpush

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
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

    List<Map> operations = []
    task.oortService = Mock(OortService) {
      1* getEntityTags("aws", "servergroup", "app-v00", "test", "us-east-1") >> {
        tags
      }
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
