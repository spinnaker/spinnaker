/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat

import com.netflix.spinnaker.fiat.model.CloudAccountProvider
import com.netflix.spinnaker.fiat.model.resources.Account
import spock.lang.Specification

class AccountProviderSpec extends Specification {

  def "should get all configured accounts"() {
    setup:
      AccountProvider provider = new AccountProvider().setAccountProviders(
          [
              new CloudAccountProvider("A").setAccounts([new Account().setName("account1")]),
              new CloudAccountProvider("B").setAccounts([new Account().setName("account2")])
          ]);

    when:
      def result = provider.getAccounts()

    then:
      result.size() == 2
      result*.name == ["account1", "account2"]
  }
}
