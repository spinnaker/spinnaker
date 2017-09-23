package com.netflix.spinnaker.clouddriver.aws.cache

import spock.lang.Specification
import spock.lang.Unroll

class KeysSpec extends Specification {

  @Unroll
  def 'key fields match namespace fields if present'() {

    expect:
    Keys.parse(key).keySet() == namespace.fields

    where:

    key                                                                                                         | namespace
    "aws:securityGroups:appname:appname-stack-detail:test:us-west-1:appname-stack-detail-v000:stack:detail:000" | Keys.Namespace.SECURITY_GROUPS
  }
}
