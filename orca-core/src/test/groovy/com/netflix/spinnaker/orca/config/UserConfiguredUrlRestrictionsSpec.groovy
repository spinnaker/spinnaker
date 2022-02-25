package com.netflix.spinnaker.orca.config

import spock.lang.Specification
import spock.lang.Unroll

class UserConfiguredUrlRestrictionsSpec extends Specification {

  @Unroll
  def 'should verify uri #uri as per restrictions provided'() {
    given:
    UserConfiguredUrlRestrictions config = new UserConfiguredUrlRestrictions.Builder()
         .withAllowedHostnamesRegex('^(.+).(.+).com(.*)|^(.\\d+).(.\\d+).(.\\d+).(.\\d+)$')
         .withRejectedIps([])
         .withAllowedSchemes(['https'])
         .withRejectLocalhost(false)
         .withRejectLinkLocal(false)
         .build()

    when:
    URI validatedUri = config.validateURI(uri)

    then:
    noExceptionThrown()
    validatedUri

    where:
    uri << ['https://www.test.com', 'https://201.152.178.212', 'https://www.test_underscore.com']
  }

  @Unroll
  def 'should verify allowedHostnamesRegex is set'() {
    given:
    UserConfiguredUrlRestrictions config = new UserConfiguredUrlRestrictions.Builder()
        .withAllowedHostnamesRegex("")
        .withRejectedIps([])
        .withAllowedSchemes(['https'])
        .withRejectLocalhost(false)
        .withRejectLinkLocal(false)
        .build()

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << ['https://www.test.com', 'https://201.152.178.212', 'https://www.test_underscore.com']
  }

}
