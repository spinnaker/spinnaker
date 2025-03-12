package com.netflix.spinnaker.orca.kato.tasks.rollingpush


import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification

class CleanUpTagsTaskSpec extends Specification {
  def "should create deleteEntityTags operations "() {
    given:
    def task = new CleanUpTagsTask()
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "")
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
        ModelUtils.tags([
        tags: [
          [
            namespace: "astrid_rules",
            name     : "tagName",
            value    : [
              imageId: "imageId"
            ],
            valueType: "object"
          ],
          [
            namespace: "astrid_rules",
            name     : "tagName2",
            value    : [
              imageId: "imageId1"
            ],
            valueType: "object"
          ]
        ]
      ]),
        ModelUtils.tags([
        tags: [
          [
            namespace: "astrid_rules",
            name     : "tagName3",
            value    : [
              imageId: "imageId1"
            ],
            valueType: "object"
          ],
          [
            namespace: "astrid_rules",
            name     : "tagName3"
          ]
        ]
      ])
    ]

    def serverGroup = new ServerGroup(
      launchConfig: [
        imageId: "imageId"
      ]
    )

    List<Map> operations = []
    task.monikerHelper = Mock(MonikerHelper) {
      1* getAppNameFromStage(stage, "app-v00") >> {
        "app"
      }
      1* getClusterNameFromStage(stage, "app-v00") >> {
        "app"
      }
      0 * _
    }
    task.cloudDriverService = Mock(CloudDriverService) {
      1* getServerGroupFromCluster("app","test", "app", "app-v00", "us-east-1", "aws") >> {
        serverGroup
      }

      1* getEntityTags("aws", "servergroup", "app-v00", "test", "us-east-1") >> {
        tags
      }

      0 * _
    }

    task.katoService = Mock(KatoService) {
      1 * requestOperations('aws', _) >> {
        operations += it[1]
        new TaskId(UUID.randomUUID().toString())
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
