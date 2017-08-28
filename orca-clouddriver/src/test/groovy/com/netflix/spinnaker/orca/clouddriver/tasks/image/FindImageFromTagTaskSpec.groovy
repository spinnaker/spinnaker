package com.netflix.spinnaker.orca.clouddriver.tasks.image

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class FindImageFromTagTaskSpec extends Specification {

  def imageFinder = Mock(ImageFinder)
  Stage stage = new Stage<>(new Pipeline("orca"), "", [packageName: 'myPackage', tags: ['foo': 'bar']])

  @Subject
  def task = new FindImageFromTagsTask(imageFinders: [imageFinder])

  def "Not finding an images should throw IllegalStateException"() {
    when:
    task.execute(stage)

    then:
    IllegalStateException ex = thrown()
    ex.message == "Could not find tagged image for package: ${stage.context.packageName} and tags: {foo=bar}"

    1 * imageFinder.getCloudProvider() >> 'aws'
  }

  def "Finding an images should set task state to SUCCEEDED"() {
    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED

    1 * imageFinder.getCloudProvider() >> 'aws'
    1 * imageFinder.byTags(stage, stage.context.packageName, stage.context.tags) >> [[:]]
  }
}
