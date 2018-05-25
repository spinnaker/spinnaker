package com.netflix.spinnaker.clouddriver.ecs.view

import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcrImageProvider
import org.junit.Test
import spock.lang.Specification

class EcrImageProviderSpec extends Specification {

  @Test
  void shouldHandleEcrRepositoryUrl() {
    given:
    EcrImageProvider ecrImageProvider = new EcrImageProvider(null, null)
    String ecrRepositoryUrl = "123456789012.dkr.ecr.us-west-2.amazonaws.com/continuous-delivery:latest"

    when:
    def canHandle = ecrImageProvider.handles(ecrRepositoryUrl)

    then:
    canHandle
  }

  @Test
  void shouldNotHandleNonEcrRepositoryUrl() {
    given:
    EcrImageProvider ecrImageProvider = new EcrImageProvider(null, null)
    String ecrRepositoryUrl = "mydockerregistry.com/continuous-delivery:latest"

    when:
    boolean canHandle = ecrImageProvider.handles(ecrRepositoryUrl)

    then:
    !canHandle
  }
}
