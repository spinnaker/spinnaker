package com.netflix.spinnaker.clouddriver.aws.security

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

class AmazonAllowedAccountsValidatorSpec extends Specification {
  NetflixAmazonCredentials credentialsWithRequiredGroup = TestCredential.named('TestAccount', [requiredGroupMembership: ['targetAccount1']])

  @Unroll
  void "should reject if allowed accounts does not intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new AmazonAllowedAccountsValidator(Mock(AccountCredentialsProvider) {
      1 * getAll() >> { [credentialsWithRequiredGroup] }
      0 * _
    })

    when:
    def description = new AllowLaunchDescription()
    description.credentials = credentialsWithRequiredGroup

    validator.validate("TestUser", allowedAccounts, description, errors)

    then:
    rejectValueCount * errors.rejectValue("credentials", "unauthorized", _)

    where:
    allowedAccounts                      || rejectValueCount
    []                                   || 1
    null                                 || 1
    ["testAccount"]                      || 0
    ["testaccount"]                      || 0
    ["TestAccount"]                      || 0
  }

  void "should allow if allow accounts intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new AmazonAllowedAccountsValidator(Mock(AccountCredentialsProvider) {
      1 * getAll() >> { [credentialsWithRequiredGroup] }
      0 * _
    })

    when:
    def description = new AllowLaunchDescription()
    description.credentials = credentialsWithRequiredGroup

    validator.validate("TestUser", ["TestAccount", "RandomAccount"], description, errors)

    then:
    0 * errors.rejectValue(_, _, _)
  }

  void "should allow if no required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new AmazonAllowedAccountsValidator(Mock(AccountCredentialsProvider))

    when:
    validator.validate("TestAccount", [], new AllowLaunchDescription(), errors)

    then:
    0 * errors.rejectValue(_, _, _)
  }
}
