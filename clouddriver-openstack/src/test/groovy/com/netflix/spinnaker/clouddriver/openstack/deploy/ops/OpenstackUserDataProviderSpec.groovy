/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.openstack.deploy.ops

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class OpenstackUserDataProviderSpec extends Specification {

  def "combines common and custom user data"() {
    given:
    def credentials = new OpenstackNamedAccountCredentials('account', 'test', 'main', 'user', 'pw', 'project', 'domain',
      'endpoint', [], false, '', null, null, null, '/some/user/data/file/udf')
    def provider = Spy(OpenstackUserDataProvider, constructorArgs: [credentials]) {
      it.getFileContents(_) >> commonUserData
    }
    def serverGroupName = 'app-stack-detail-v001'
    def region = 'west'

    when:
    def userData = provider.getUserData(serverGroupName, region, customUserData)

    then:
    userData == expectedUserData
    noExceptionThrown()

    where:
    customUserData            | commonUserData           || expectedUserData
    null                      | null                      | ''
    ''                        | null                      | ''
    null                      | ''                        | ''
    null                      | 'echo "common user data"' | 'echo "common user data"\n'
    ''                        | 'echo "common user data"' | 'echo "common user data"\n'
    'echo "custom user data"' | null                      | 'echo "custom user data"'
    'echo "custom user data"' | ''                        | 'echo "custom user data"'
    'echo "custom user data"' | 'echo "common user data"' | 'echo "common user data"\necho "custom user data"'
    '%%account%%'             | '%%region%%'              | 'west\n%%account%%'
  }


  def "handles unreadable user data file"() {
      given:
      def credentials = new OpenstackNamedAccountCredentials('account', 'test', 'main', 'user', 'pw', 'project', 'domain',
        'endpoint', [], false, '', null, null, null, userDataFile)
      def provider = new OpenstackUserDataProvider(credentials)
      def serverGroupName = 'app-stack-detail-v001'
      def region = 'west'

      when:
      def userData = provider.getUserData(serverGroupName, region, 'custom user data')

      then:
      userData == 'custom user data'
      noExceptionThrown()

      where:
      userDataFile << [null, '', '/a/non/existant/file/udf']
  }

   def "ensure replace tokens works"() {
     given:
     def credentials = new OpenstackNamedAccountCredentials('my-account', 'test', 'main', 'user', 'pw', 'project', 'domain',
       'endpoint', [], false, '', null, null, null, '/user/data/file/udf')
     def provider = Spy(OpenstackUserDataProvider, constructorArgs: [credentials]) {
       it.getFileContents(_) >> rawUserData
     }
     def serverGroupName = 'myapp-dev-green-v001'
     def region = 'west'

     when:
     def userData = provider.getUserData(serverGroupName, region, '')

     then:
     userData == expectedUserData

     where:
     rawUserData           | expectedUserData
     ''                    | ''
     null                  | ''
     '%%account%%'         | 'my-account\n'
     '%account%'           | '%account%\n'
     '%%accounttype%%'     | 'main\n'
     '%%env%%'             | 'test\n'
     '%%region%%'          | 'west\n'
     '%%env%%\n%%region%%' | 'test\nwest\n'
     '%%app%%'             | 'myapp\n'
     '%%stack%%'           | 'dev\n'
     '%%detail%%'          | 'green\n'
     '%%cluster%%'         | 'myapp-dev-green\n'
     '%%group%%'           | 'myapp-dev-green-v001\n'
     '%%autogrp%%'         | 'myapp-dev-green-v001\n'
     '%%launchconfig%%'    | 'myapp-dev-green-v001\n'
   }
}
