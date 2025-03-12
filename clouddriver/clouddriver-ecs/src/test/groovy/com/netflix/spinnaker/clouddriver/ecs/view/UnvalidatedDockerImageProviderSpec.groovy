package com.netflix.spinnaker.clouddriver.ecs.view

import com.netflix.spinnaker.clouddriver.ecs.provider.view.UnvalidatedDockerImageProvider
import org.junit.jupiter.api.Test
import spock.lang.Specification

class UnvalidatedDockerImageProviderSpec extends Specification {

  @Test
  void shouldNotHandleEcrRepositoryUrl() {
    given:
    UnvalidatedDockerImageProvider unvalidatedDockerImageProvider = new UnvalidatedDockerImageProvider()
    String ecrRepositoryUrl = "123456789012.dkr.ecr.us-west-2.amazonaws.com/continuous-delivery:latest"

    when:
    boolean canHandle = unvalidatedDockerImageProvider.handles(ecrRepositoryUrl)

    then:
    !canHandle
  }

  @Test
  void shouldHandleNonEcrRepositoryUrl() {
    given:
    UnvalidatedDockerImageProvider unvalidatedDockerImageProvider = new UnvalidatedDockerImageProvider()
    String ecrRepositoryUrl = "mydockerregistry.com/continuous-delivery:latest"

    when:
    boolean canHandle = unvalidatedDockerImageProvider.handles(ecrRepositoryUrl)

    then:
    canHandle
  }
}
