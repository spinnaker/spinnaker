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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class GoogleUserDataProviderSpec extends Specification {

  @Unroll
  def "combines common and custom user data"() {
    given:
    def computeMock = Mock(Compute)

    def credentials = new GoogleNamedAccountCredentials.Builder().accountType('test').environment('env').userDataFile("file").compute(computeMock).build()
    def provider = Spy(GoogleUserDataProvider) {
      it.getFileContents(_) >> commonUserData
    }
    def serverGroupName = 'app-stack-detail-v001'
    def instanceTemplateName = 'app-stack-detail-v001-123'
    def description = new BasicGoogleDeployDescription(region: 'region', accountName: 'account', credentials: credentials)

    when:
    def userData = provider.getUserData(serverGroupName, instanceTemplateName, description, credentials, customUserData)

    then:
    userData == expectedUserData
    noExceptionThrown()

    where:
    customUserData            | commonUserData           || expectedUserData
    null                      | null                     || [:]
    ''                        | null                     || [:]
    null                      | ''                       || [:]
    null                      | 'user_data=common'       || ['user_data':'common']
    ''                        | 'user_data=common'       || ['user_data':'common']
    'user_data=common'        | null                     || ['customUserData' : customUserData, 'user_data':'common']
    'user_data=common'        | ''                       || ['customUserData' : customUserData, 'user_data':'common']
    'user_data=common'        | 'user_data=common'       || ['customUserData' : customUserData, 'user_data':'common']
    '%%account%%'             | '%%region%%'             || ['customUserData': customUserData, '%%account%%':'%%account%%', 'region':'region']
  }

  @Unroll
  def "handles unreadable user data file"() {
    given:
    def computeMock = Mock(Compute)

    def credentials = new GoogleNamedAccountCredentials.Builder().accountType('test').environment('env').userDataFile("file").compute(computeMock).build()
    def provider = new GoogleUserDataProvider()
    def serverGroupName = 'app-stack-detail-v001'
    def instanceTemplateName = 'app-stack-detail-v001-123'
    def description = new BasicGoogleDeployDescription(region: 'region', accountName: 'account', credentials: credentials)

    when:
    def userData = provider.getUserData(serverGroupName, instanceTemplateName, description, credentials, 'custom user data')

    then:
    userData == ['customUserData': 'custom user data', 'custom user data':'custom user data']
    noExceptionThrown()

    where:
    userDataFile << [null, '', '/a/non/existent/file/udf']
  }

  @Unroll
  def "ensure replace tokens works"() {
    given:
    def computeMock = Mock(Compute)

    def credentials = new GoogleNamedAccountCredentials.Builder().accountType('test').environment('env').userDataFile("file").compute(computeMock).build()
    def provider = Spy(GoogleUserDataProvider) {
      it.getFileContents(_) >> rawUserData
    }
    def serverGroupName = 'app-stack-detail-v001'
    def instanceTemplateName = 'app-stack-detail-v001-123'
    def description = new BasicGoogleDeployDescription(region: 'region', accountName: 'account', credentials: credentials)

    when:
    def userData = provider.getUserData(serverGroupName, instanceTemplateName, description, credentials, '')

    then:
    userData == expectedUserData

    where:
    rawUserData           || expectedUserData
    ''                    || [:]
    null                  || [:]
    '%%account%%'         || ['account':'account']
    '%account%'           || ['%account%':'%account%']
    '%%accounttype%%'     || ['test':'test']
    '%%env%%'             || ['env':'env']
    '%%region%%'          || ['region':'region']
    '%%env%%\n%%region%%' || ['env':'env', 'region':'region']
    '%%app%%'             || ['app':'app']
    '%%stack%%'           || [:]
    '%%detail%%'          || ['detail':'detail']
    '%%cluster%%'         || ['app-stack-detail':'app-stack-detail']
    '%%group%%'           || ['app-stack-detail-v001':'app-stack-detail-v001']
    '%%launchconfig%%'    || ['app-stack-detail-v001-123':'app-stack-detail-v001-123']
  }
}
