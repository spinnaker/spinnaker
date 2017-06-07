package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.spinnaker.clouddriver.dcos.DcosClientCompositeKey
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import mesosphere.dcos.client.Config
import spock.lang.Specification

class BaseSpecification extends Specification {
  public static final DEFAULT_ACCOUNT = 'test'
  public static final DEFAULT_REGION = 'us-test-1'
  public static final DEFAULT_GROUP = 'default'
  public static final DEFAULT_SECRET_STORE = 'default'
  public static
  final DEFAULT_COMPOSITE_KEY = DcosClientCompositeKey.buildFromVerbose(DEFAULT_ACCOUNT, DEFAULT_REGION).get()
  public static final BAD_ACCOUNT = 'bad-acct'

  def defaultCredentialsBuilder() {
    DcosAccountCredentials.builder().account(DEFAULT_ACCOUNT).environment('test').accountType('test').requiredGroupMembership([])
      .dockerRegistries([new DcosConfigurationProperties.LinkedDockerRegistryConfiguration(accountName: 'dockerReg')])
      .clusters([DcosClusterCredentials.builder().key(DEFAULT_COMPOSITE_KEY).dcosUrl('https://test.url.com').secretStore('default').dcosConfig(Config.builder().build()).build()])
  }

  def emptyCredentialsBuilder() {
    new DcosAccountCredentials(null, null, null, null, null, null)
  }
}
