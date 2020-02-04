/*
 * Copyright 2020 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.codebuild

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import spock.lang.Specification

class AwsCodeBuildAccountRepositorySpec extends Specification{
  AwsCodeBuildAccountRepository repository = new AwsCodeBuildAccountRepository()
  AwsCodeBuildAccount account1 = Mock(AwsCodeBuildAccount)
  AwsCodeBuildAccount account2 = Mock(AwsCodeBuildAccount)

  def "getAccount should return account if it exists in the repository"() {
    given:
    repository.addAccount("account1", account1)
    repository.addAccount("account2", account2)

    when:
    def outputAccount1 = repository.getAccount("account1")
    def outputAccount2 = repository.getAccount("account2")

    then:
    outputAccount1 == account1
    outputAccount2 == account2
  }

  def "getAccount should throw NotFoundException if account doesn't exist in the repository"() {
    given:
    repository.addAccount("account1", account1)

    when:
    repository.getAccount("account2")

    then:
    NotFoundException exception = thrown()
    exception.getMessage() == "No AWS CodeBuild account with name account2 is configured"
  }

  def "getAccountNames should return all accounts in the repository"() {
    given:
    repository.addAccount("account1", account1)
    repository.addAccount("account2", account2)

    when:
    String[] accounts = repository.getAccountNames()

    then:
    accounts.length == 2
    accounts.contains("account1")
    accounts.contains("account2")
  }
}
