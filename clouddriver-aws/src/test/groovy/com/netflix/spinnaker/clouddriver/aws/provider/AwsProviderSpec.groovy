/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class AwsProviderSpec extends Specification {

  @Subject
  AwsProvider eurekaAwsProvider

  @Subject
  AwsProvider awsProvider

  def setup() {
    def eurekaAccount1 = new NetflixAmazonCredentials("my-ci-account",
            "ci",
            "my-ci-account",
            "123",
            "my-ci-account-keypair",
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            "1.0.0.2:8080/eureka/v2",
            true,
            null,
            false,
            null,
            false,
            false,
            false)
    def eurekaAccount2 = new NetflixAmazonCredentials("my-qa-account",
            "qa",
            "my-qa-account",
            "123",
            "my-qa-account-keypair",
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            "1.0.0.3:8080/eureka/v2",
            true,
            null,
            false,
            null,
            false,
            false,
            false)

    def eurekaAccounts = [ eurekaAccount1, eurekaAccount2 ]

    def eurekaRepos = Stub(AccountCredentialsRepository) {
      getAll() >> eurekaAccounts
    }

    eurekaAwsProvider = new AwsProvider(eurekaRepos, [])

    def account1 = new NetflixAmazonCredentials("my-ci-account",
            "ci",
            "my-ci-account",
            "123",
            "my-ci-account-keypair",
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            true,
            null,
            false,
            null,
            false,
            false,
            false)
    def account2 = new NetflixAmazonCredentials("my-qa-account",
            "qa",
            "my-qa-account",
            "456",
            "my-qa-account-keypair",
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            true,
            null,
            false,
            null,
            false,
            false,
            false)

    def accounts = [ account1, account2 ]

    def repos = Stub(AccountCredentialsRepository) {
      getAll() >> accounts
    }

    awsProvider = new AwsProvider(repos, [])
  }

  void "getInstanceKey returns CI account that matches both AWS account ID and eureka host"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-ci-account", instanceId: "i-045"]
    def result = eurekaAwsProvider.getInstanceKey(attributes, "us-east-1")

    then:
    result == "aws:instances:my-ci-account:us-east-1:i-045"
  }

  void "getInstanceHealthKey returns CI account that matches both AWS account ID and eureka host"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-ci-account", instanceId: "i-045"]
    def result = eurekaAwsProvider.getInstanceHealthKey(attributes, "us-east-1", "xyz")

    then:
    result == "aws:health:i-045:my-ci-account:us-east-1:xyz"
  }

  void "getInstanceKey returns QA account that matches both AWS account ID and eureka host"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-qa-account", instanceId: "i-032"]
    def result = eurekaAwsProvider.getInstanceKey(attributes, "us-east-1")

    then:
    result == "aws:instances:my-qa-account:us-east-1:i-032"
  }

  void "getInstanceHealthKey returns QA account that matches both AWS account ID and eureka host"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-qa-account", instanceId: "i-032"]
    def result = eurekaAwsProvider.getInstanceHealthKey(attributes, "us-east-1", "jkl")

    then:
    result == "aws:health:i-032:my-qa-account:us-east-1:jkl"
  }

  void "getInstanceKey returns CI account that just matches AWS account ID, although multiple eurekas per account is allowed"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-ci-account", instanceId: "i-045"]
    def result = awsProvider.getInstanceKey(attributes, "us-east-1")

    then:
    result == "aws:instances:my-ci-account:us-east-1:i-045"
  }

  void "getInstanceHealthKey returns CI account that just matches AWS account ID, although multiple eurekas per account is allowed"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-ci-account", instanceId: "i-045"]
    def result = awsProvider.getInstanceHealthKey(attributes, "us-east-1", "xyz")

    then:
    result == "aws:health:i-045:my-ci-account:us-east-1:xyz"
  }

  void "getInstanceKey returns QA account that just matches AWS account ID, although multiple eurekas per account is allowed"() {
    when:
    def attributes = [accountId: "456", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-qa-account", instanceId: "i-032"]
    def result = awsProvider.getInstanceKey(attributes, "us-east-1")

    then:
    result == "aws:instances:my-qa-account:us-east-1:i-032"
  }

  void "getInstanceHealthKey returns QA account that just matches AWS account ID, although multiple eurekas per account is allowed"() {
    when:
    def attributes = [accountId: "456", allowMultipleEurekaPerAccount: true, eurekaAccountName: "my-qa-account", instanceId: "i-032"]
    def result = awsProvider.getInstanceHealthKey(attributes, "us-east-1", "jkl")

    then:
    result == "aws:health:i-032:my-qa-account:us-east-1:jkl"
  }

  void "getInstanceKey returns CI account that just matches AWS account ID, and multiple eurekas per account is not allowed"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: false, eurekaAccountName: "my-ci-account", instanceId: "i-045"]
    def result = awsProvider.getInstanceKey(attributes, "us-east-1")

    then:
    result == "aws:instances:my-ci-account:us-east-1:i-045"
  }

  void "getInstanceHealthKey returns CI account that just matches AWS account ID, and multiple eurekas per account is not allowed"() {
    when:
    def attributes = [accountId: "123", allowMultipleEurekaPerAccount: false, eurekaAccountName: "my-ci-account", instanceId: "i-045"]
    def result = awsProvider.getInstanceHealthKey(attributes, "us-east-1", "xyz")

    then:
    result == "aws:health:i-045:my-ci-account:us-east-1:xyz"
  }

  void "getInstanceKey returns QA account that just matches AWS account ID, and multiple eurekas per account is not allowed"() {
    when:
    def attributes = [accountId: "456", allowMultipleEurekaPerAccount: false, eurekaAccountName: "my-qa-account", instanceId: "i-032"]
    def result = awsProvider.getInstanceKey(attributes, "us-east-1")

    then:
    result == "aws:instances:my-qa-account:us-east-1:i-032"
  }

  void "getInstanceHealthKey returns QA account that just matches AWS account ID, and multiple eurekas per account is not allowed"() {
    when:
    def attributes = [accountId: "456", allowMultipleEurekaPerAccount: false, eurekaAccountName: "my-qa-account", instanceId: "i-032"]
    def result = awsProvider.getInstanceHealthKey(attributes, "us-east-1", "jkl")

    then:
    result == "aws:health:i-032:my-qa-account:us-east-1:jkl"
  }
}
