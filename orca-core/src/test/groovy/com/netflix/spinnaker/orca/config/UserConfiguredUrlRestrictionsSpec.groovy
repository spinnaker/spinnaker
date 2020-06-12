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

}
