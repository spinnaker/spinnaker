package com.netflix.spinnaker.clouddriver.ecs.view

import com.netflix.spinnaker.clouddriver.ecs.provider.view.UnvalidatedDockerImageProvider
import spock.lang.Specification

class UnvalidatedDockerImageProviderSpec extends Specification {

  def shouldNotHandleEcrRepositoryUrl() {
    given:
    UnvalidatedDockerImageProvider unvalidatedDockerImageProvider = new UnvalidatedDockerImageProvider()
    String ecrRepositoryUrl = "123456789012.dkr.ecr.us-west-2.amazonaws.com/continuous-delivery:latest"

    when:
    boolean canHandle = unvalidatedDockerImageProvider.handles(ecrRepositoryUrl)

    then:
    !canHandle
  }

  def shouldHandleNonEcrRepositoryUrl() {
    given:
    UnvalidatedDockerImageProvider unvalidatedDockerImageProvider = new UnvalidatedDockerImageProvider()
    String ecrRepositoryUrl = "mydockerregistry.com/continuous-delivery:latest"

    when:
    boolean canHandle = unvalidatedDockerImageProvider.handles(ecrRepositoryUrl)

    then:
    canHandle
  }
}
