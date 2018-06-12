package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import spock.lang.Specification
import spock.lang.Subject


class DefaultProviderLookupServiceSpec extends Specification {

  ClouddriverService clouddriverService = Mock(ClouddriverService)

  @Subject
  def defaultProviderLookupService = new DefaultProviderLookupService(clouddriverService)

  def "it should replace requiredGroupMemberships if permissions present"() {
    when:
    defaultProviderLookupService.refreshCache()
    def accounts = defaultProviderLookupService.accounts

    then:
    accounts != null
    !accounts.isEmpty()
    accounts[0].permissions != null
    accounts[0].permissions == expected

    1 * clouddriverService.getAccountDetails() >> {
      [new ClouddriverService.AccountDetails(requiredGroupMembership: origRequiredGroupMemberships, permissions: origPerms)]
    }
    0 * _

    where:
    origPerms                    | origRequiredGroupMemberships | expected
    null                         | []                           | [:]
    [:]                          | ['foo']                      | [:]
    null                         | ['mgmt-spinnaker']           | [READ: ['mgmt-spinnaker'], WRITE: ['mgmt-spinnaker']]
    null                         | ['MGMT-spinnaker']           | [READ: ['mgmt-spinnaker'], WRITE: ['mgmt-spinnaker']]
    [WRITE: ['bacon-spinnaker']] | ['mgmt-spinnaker']           | [WRITE: ['bacon-spinnaker']]
    [WRITE: ['BACON-spinnaker']] | ['mgmt-spinnaker']           | [WRITE: ['bacon-spinnaker']]
  }
}
