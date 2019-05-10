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
    def repoName = ecrImageProvider.extractEcrRepositoryName(ecrRepositoryUrl)
    def accountId = ecrImageProvider.extractAwsAccountId(ecrRepositoryUrl)
    def awsRegion = ecrImageProvider.extractAwsRegion(ecrRepositoryUrl)
    def ecrIdentifier = ecrImageProvider.extractEcrIdentifier(repoName, ecrRepositoryUrl)

    then:
    canHandle &&
    accountId == "123456789012" &&
    repoName == "continuous-delivery" &&
    awsRegion == "us-west-2" &&
    ecrIdentifier == "latest"
  }

  @Test
  void shouldHandleEcrSubOrgRepositoryUrl() {
    given:
    EcrImageProvider ecrImageProvider = new EcrImageProvider(null, null)
    String ecrRepositoryUrl = "123456789012.dkr.ecr.us-west-2.amazonaws.com/sub-org/continuous-delivery:latest"

    when:
    def canHandle = ecrImageProvider.handles(ecrRepositoryUrl)
    def repoName = ecrImageProvider.extractEcrRepositoryName(ecrRepositoryUrl)
    def accountId = ecrImageProvider.extractAwsAccountId(ecrRepositoryUrl)
    def awsRegion = ecrImageProvider.extractAwsRegion(ecrRepositoryUrl)
    def ecrIdentifier = ecrImageProvider.extractEcrIdentifier(repoName, ecrRepositoryUrl)

    then:
    accountId == "123456789012" &&
    repoName == "sub-org/continuous-delivery" &&
    awsRegion == "us-west-2" &&
    ecrIdentifier == "latest"
  }

  @Test
  void shouldHandleEcrSha256RepositoryUrl() {
    given:
    EcrImageProvider ecrImageProvider = new EcrImageProvider(null, null)
    String ecrRepositoryUrl = "123456789012.dkr.ecr.us-east-1.amazonaws.com/continuous-delivery@sha256:e87afa4e9a1b5b2b10b596526881acb6e7007dbff43f37270921ba84dbeda428"

    when:
    def canHandle = ecrImageProvider.handles(ecrRepositoryUrl)
    def repoName = ecrImageProvider.extractEcrRepositoryName(ecrRepositoryUrl)
    def accountId = ecrImageProvider.extractAwsAccountId(ecrRepositoryUrl)
    def awsRegion = ecrImageProvider.extractAwsRegion(ecrRepositoryUrl)
    def ecrIdentifier = ecrImageProvider.extractEcrIdentifier(repoName, ecrRepositoryUrl)

    then:
    accountId == "123456789012" &&
    repoName == "continuous-delivery" &&
    awsRegion == "us-east-1" &&
    ecrIdentifier == "sha256:e87afa4e9a1b5b2b10b596526881acb6e7007dbff43f37270921ba84dbeda428"
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

  @Test
  void shouldNotHandleShortEcrRepoName() {
    given:
    EcrImageProvider ecrImageProvider = new EcrImageProvider(null, null)
    String ecrRepositoryUrl = "123456789012.dkr.ecr.us-west-2.amazonaws.com/n:latest"

    when:
    boolean canHandle = ecrImageProvider.handles(ecrRepositoryUrl)

    then:
    !canHandle
  }

  @Test
  void shouldNotHandleMissingRepoName() {
    given:
    EcrImageProvider ecrImageProvider = new EcrImageProvider(null, null)
    String domainNameOnly = "123456789012.dkr.ecr.us-east-1.amazonaws.com"
    String noRepoName = "123456789012.dkr.ecr.us-east-1.amazonaws.com/"
    String subOrgNoRepoName = "123456789012.dkr.ecr.us-east-1.amazonaws.com/sub-org/"

    when:
    boolean canHandleDomainNameOnly = ecrImageProvider.handles(domainNameOnly)
    boolean canHandleNoRepoName = ecrImageProvider.handles(noRepoName)
    boolean canHandleSubOrgNoRepoName = ecrImageProvider.handles(subOrgNoRepoName)

    then:
    !canHandleDomainNameOnly && !canHandleNoRepoName && !canHandleSubOrgNoRepoName
  }
}
