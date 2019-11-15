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
 */

package com.netflix.spinnaker.fiat.roles.file

import com.google.common.io.Resources
import com.netflix.spinnaker.fiat.permissions.ExternalUser
import spock.lang.Specification

class FileBasedUserRolesProviderSpec extends Specification {

  FileBasedUserRolesProvider.ConfigProps configProps = new FileBasedUserRolesProvider.ConfigProps()

  def "should read file based permissions"() {
    setup:
    URI uri = Resources.getResource("fiat-test-permissions.yml").toURI()
    configProps.path = new File(uri).absolutePath
    FileBasedUserRolesProvider provider = new FileBasedUserRolesProvider(configProps: configProps)

    when:
    def result1 = provider.loadRoles(externalUser("batman"))
    def result2 = provider.loadRoles(externalUser("foo"))
    def result3 = provider.loadRoles(externalUser("spiderman"))

    then:
    result1.name.containsAll(["crimefighter", "jokerjailer"])
    result2.name.containsAll(["bar", "baz"])
    result3 == []

    when:
    def result4 = provider.multiLoadRoles([externalUser("batman")])
    def result5 = provider.multiLoadRoles([externalUser("batman"), externalUser("foo")])
    def result6 = provider.multiLoadRoles([externalUser("spiderman")])

    then:
    result4.containsKey("batman")
    !result4.containsKey("foo")

    result5.keySet().containsAll("batman", "foo")
    result6.keySet().size() == 0
  }

  private static ExternalUser externalUser(String id) {
    return new ExternalUser().setId(id)
  }
}
