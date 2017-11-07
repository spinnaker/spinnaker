/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.security

import com.netflix.spinnaker.cats.agent.NoopExecutionInstrumentation
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.module.DefaultCatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.test.TestAccountAwareAgent
import com.netflix.spinnaker.cats.test.TestAgent
import com.netflix.spinnaker.cats.test.TestAgentSchedulerAwareProvider
import com.netflix.spinnaker.cats.test.TestProvider
import com.netflix.spinnaker.cats.test.TestScheduler
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ProviderUtilsSpec extends Specification {

  @Shared
  Provider provider

  @Shared
  AccountCredentialsRepository accountCredentialsRepository

  @Shared
  def awsAccount1 = buildNetflixAmazonCredentials("aws-account-1")

  @Shared
  def awsAccount2 = buildNetflixAmazonCredentials("aws-account-2")

  @Shared
  def awsAccount3 = buildNetflixAmazonCredentials("aws-account-3")

  @Shared
  def awsAccount4 = buildNetflixAmazonCredentials("aws-account-4")

  @Shared
  def googleAccount1 = buildGoogleNamedAccountCredentials("google-account-1")

  @Shared
  def googleAccount2 = buildGoogleNamedAccountCredentials("google-account-2")

  @Shared
  def googleAccount3 = buildGoogleNamedAccountCredentials("google-account-3")

  @Shared
  def googleAccount4 = buildGoogleNamedAccountCredentials("google-account-4")

  @Shared
  def googleAccount5 = buildGoogleNamedAccountCredentials("google-account-5")

  def setupSpec() {
    def agents = [
      new TestAgent(),
      new TestAccountAwareAgent(accountName: "some-account-1"),
      new TestAgent(),
      new TestAccountAwareAgent(accountName: "some-account-2")
    ]

    provider = new TestProvider(agents)

    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    accountCredentialsRepository.save("aws-account-1", awsAccount1)
    accountCredentialsRepository.save("aws-account-2", awsAccount2)
    accountCredentialsRepository.save("google-account-1", googleAccount1)
    accountCredentialsRepository.save("google-account-2", googleAccount2)
    accountCredentialsRepository.save("google-account-3", googleAccount3)
    accountCredentialsRepository.save("aws-account-3", awsAccount3)
  }

  void "should collect account names from all account aware agents"() {
    when:
      def scheduledAccounts = ProviderUtils.getScheduledAccounts(provider)

    then:
      scheduledAccounts == ["some-account-1", "some-account-2"] as Set
  }

  @Unroll
  void "should collect accounts matching specified credentials type"() {
    when:
      def accountSet = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, credentialsType)

    then:
      accountSet.collect { it.name } as Set == accountNameSet

    where:
      credentialsType               | accountNameSet
      GoogleNamedAccountCredentials | ["google-account-1", "google-account-2", "google-account-3"] as Set
      NetflixAmazonCredentials      | ["aws-account-1", "aws-account-2", "aws-account-3"] as Set
  }

  void "should reschedule specified agents"() {
    setup:
      def testAgent1 = new TestAgent()
      def testAgent2 = new TestAgent()
      def testAgent3 = new TestAgent()
      def agentSchedulerAwareProvider = new TestAgentSchedulerAwareProvider(agents: [testAgent1])
      def namedCacheFactory = new InMemoryNamedCacheFactory()
      def scheduler = new TestScheduler()
      def executionInstrumentation = new NoopExecutionInstrumentation()

    when:
      new DefaultCatsModule([agentSchedulerAwareProvider], namedCacheFactory, scheduler, executionInstrumentation)

    then:
      scheduler.scheduled.collect { it.agent } == [testAgent1]

    when:
      ProviderUtils.rescheduleAgents(agentSchedulerAwareProvider, [testAgent2, testAgent3])

    then:
      scheduler.scheduled.collect { it.agent } == [testAgent1, testAgent2, testAgent3]
  }

  @Unroll
  void "should calculate account deltas for specified credentials type"() {
    when:
      def (ArrayList<NetflixAmazonCredentials> accountsToAdd, List<String> namesOfDeletedAccounts) =
        ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, credentialsType, desiredAccounts)

    then:
      accountsToAdd as Set == expectedAccountsToAdd
      namesOfDeletedAccounts as Set == expectedNamesOfDeletedAccounts

    where:
      credentialsType               | desiredAccounts                                                  | expectedAccountsToAdd                   | expectedNamesOfDeletedAccounts
      NetflixAmazonCredentials      | [awsAccount2, awsAccount4]                                       | [awsAccount4] as Set                    | ["aws-account-1", "aws-account-3"] as Set
      GoogleNamedAccountCredentials | [googleAccount1, googleAccount2, googleAccount4, googleAccount5] | [googleAccount4, googleAccount5] as Set | ["google-account-3"] as Set
  }

  void "should unschedule and deregister agents associated with deleted accounts"() {
    setup:
      def testAgent1 = new TestAccountAwareAgent(accountName: "some-account-1")
      def testAgent2 = new TestAccountAwareAgent(accountName: "some-account-2")
      def testAgent3 = new TestAccountAwareAgent(accountName: "some-account-3")
      def testAgent4 = new TestAccountAwareAgent(accountName: "some-account-4")
      def testAgent5 = new TestAccountAwareAgent(accountName: "some-account-5")
      def agentSchedulerAwareProvider =
        new TestAgentSchedulerAwareProvider(agents: [testAgent1, testAgent2, testAgent3, testAgent4, testAgent5])
      def namedCacheFactory = new InMemoryNamedCacheFactory()
      def scheduler = new TestScheduler()
      def executionInstrumentation = new NoopExecutionInstrumentation()
      def catsModule

    when:
      catsModule = new DefaultCatsModule([agentSchedulerAwareProvider], namedCacheFactory, scheduler, executionInstrumentation)

    then:
      scheduler.scheduled.collect { it.agent } == [testAgent1, testAgent2, testAgent3, testAgent4, testAgent5]

    when:
      ProviderUtils.unscheduleAndDeregisterAgents(["some-account-2", "some-account-3", "some-account-5"], catsModule)

    then:
      scheduler.scheduled.collect { it.agent } == [testAgent1, testAgent4]
      agentSchedulerAwareProvider.agents == [testAgent1, testAgent4]
  }

  private static NetflixAmazonCredentials buildNetflixAmazonCredentials(String accountName) {
    new NetflixAmazonCredentials(accountName,
                                 "some-env",
                                 "some-account-type",
                                 "account-id-123",
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 false,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null,
                                 null)
  }

  private static GoogleNamedAccountCredentials buildGoogleNamedAccountCredentials(String accountName) {
    new GoogleNamedAccountCredentials.Builder().name(accountName).credentials(new FakeGoogleCredentials()).build()
  }
}
