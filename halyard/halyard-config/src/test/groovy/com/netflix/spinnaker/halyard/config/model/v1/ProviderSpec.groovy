/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1

import com.netflix.spinnaker.halyard.config.model.v1.node.Account
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder
import spock.lang.Specification

class ProviderSpec extends Specification {
  void "provider shows null primary account "() {
    setup:
    def test = new TestProvider()

    when:
    def primary = test.getPrimaryAccount()

    then:
    primary == null
  }

  void "provider shows a primary account"() {
    setup:
    def deploymentEnvironment = new DeploymentEnvironment()
    def deploymentConfiguration = new DeploymentConfiguration()
    deploymentConfiguration.deploymentEnvironment = deploymentEnvironment
    deploymentEnvironment.parent = deploymentConfiguration
    def test = new TestProvider()
    test.parent = deploymentEnvironment

    def name = "my-account"
    def account = new TestAccount().setName(name)
    test.accounts.add(account)

    when:
    def primary = test.getPrimaryAccount()

    then:
    primary == name
  }

  void "provider shows a primary account avoiding bootstrap account"() {
    setup:
    def deploymentEnvironment = new DeploymentEnvironment()
    deploymentEnvironment.bootstrapOnly = true
    deploymentEnvironment.accountName = "my-bootstrap-account"

    def deploymentConfiguration = new DeploymentConfiguration()
    deploymentConfiguration.deploymentEnvironment = deploymentEnvironment
    deploymentEnvironment.parent = deploymentConfiguration
    def test = new TestProvider()
    test.parent = deploymentEnvironment

    def bootstrapname = "my-bootstrap-account"
    def bootstrapaccount = new TestAccount().setName(bootstrapname)
    test.accounts.add(bootstrapaccount)

    def name = "my-account"
    def account = new TestAccount().setName(name)
    test.accounts.add(account)

    when:
    def primary = test.getPrimaryAccount()

    then:
    primary == name
  }

  void "provider shows no primary account after all accounts are removed"() {
    setup:
    def test = new TestProvider()
    def name = "my-account"
    def account = new TestAccount().setName(name)
    test.accounts.add(account)
    test.accounts.clear()

    when:
    def primary = test.getPrimaryAccount()

    then:
    primary == null
  }

  void "provider shows no primary account if only account is bootstrap only"() {
    setup:
    def deploymentEnvironment = new DeploymentEnvironment()
    deploymentEnvironment.bootstrapOnly = true
    deploymentEnvironment.accountName = "my-bootstrap-account"

    def deploymentConfiguration = new DeploymentConfiguration()
    deploymentConfiguration.deploymentEnvironment = deploymentEnvironment
    deploymentEnvironment.parent = deploymentConfiguration
    def test = new TestProvider()
    test.parent = deploymentEnvironment

    def name = "my-bootstrap-account"
    def account = new TestAccount().setName(name)
    test.accounts.add(account)

    when:
    def primary = test.getPrimaryAccount()

    then:
    primary == null
  }

  void "provider shows a primary account after it's removed but another account remains"() {
    setup:
    def deploymentEnvironment = new DeploymentEnvironment()
    def deploymentConfiguration = new DeploymentConfiguration()
    deploymentConfiguration.deploymentEnvironment = deploymentEnvironment
    deploymentEnvironment.parent = deploymentConfiguration
    def test = new TestProvider()
    test.parent = deploymentEnvironment

    def name = "my-account"
    def newName = "my-new-account"
    def account = new TestAccount().setName(name)
    def newAccount = new TestAccount().setName(newName)
    test.accounts.add(account)
    test.accounts.add(newAccount)

    when:
    def primary = test.getPrimaryAccount()

    then:
    primary == name

    when:
    test.accounts.removeIf({it.name == name})
    primary = test.getPrimaryAccount()

    then:
    primary == newName
  }

  class TestProvider extends Provider<TestAccount> {
    @Override
    Provider.ProviderType providerType() {
      return null
    }
  }

  class TestAccount extends Account { }
}
