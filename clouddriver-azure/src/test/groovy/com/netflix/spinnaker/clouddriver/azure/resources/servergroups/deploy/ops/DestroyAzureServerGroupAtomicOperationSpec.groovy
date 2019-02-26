/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroups.deploy.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.EnableDisableDestroyAzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops.DestroyAzureServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops.converters.DestroyAzureServerGroupAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class DestroyAzureServerGroupAtomicOperationSpec extends Specification {
  static final ACCOUNT_NAME = "my-azure-account"
  private static final CLOUD_PROVIDER = "azure"
  private static final ACCOUNT_CLIENTID = "azureclientid"
  private static final ACCOUNT_TENANTID = "azuretenantid1"
  private static final ACCOUNT_APPKEY = "azureappkey1"
  private static final SUBSCRIPTION_ID = "azuresubscriptionid1"
  private static final DEFAULT_KEY_VAULT = "azuredefaultkeyvault"
  private static final DEFAULT_RESOURCE_GROUP = "azuredefaultresourcegroup"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DestroyAzureServerGroupAtomicOperationConverter converter

  @Shared
  AzureCredentials azureCredentials

  @Shared
  AzureNamedAccountCredentials credentials

  @Shared
  AccountCredentialsProvider accountCredentialsProvider

  def setupSpec() {
    converter = new DestroyAzureServerGroupAtomicOperationConverter(objectMapper: mapper)
    azureCredentials = new AzureCredentials(ACCOUNT_CLIENTID, ACCOUNT_TENANTID, ACCOUNT_APPKEY, SUBSCRIPTION_ID, DEFAULT_KEY_VAULT, DEFAULT_RESOURCE_GROUP, "", "")

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    credentials = Mock(AzureNamedAccountCredentials)
    credentials.getAccountName() >> ACCOUNT_NAME
    credentials.getName() >> ACCOUNT_NAME
    credentials.getCredentials() >> azureCredentials
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    accountCredentialsProvider = Mock(AccountCredentialsProvider)
    accountCredentialsProvider.getCredentials(_) >> credentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "Create DestroyAzureServerGroupAtomicOperation object simple test"() {
    setup:
    def input = '''{ "serverGroupName": "testazure-web1-d1-v000", "name": "testazure-web1-d1-v000", "account" : "my-azure-account", "cloudProvider" : "azure", "appName" : "testazure", "region": "westus", "credentials": "my-azure-account" }'''

    when:
    DestroyAzureServerGroupAtomicOperation operation = converter.convertOperation(mapper.readValue(input, Map))
    EnableDisableDestroyAzureServerGroupDescription description = converter.convertDescription(mapper.readValue(input, Map))

    then:
    operation
    description.name == "testazure-web1-d1-v000"
    description.accountName == "my-azure-account"
    description.region == "westus"
  }

  void "Create DestroyAzureServerGroupAtomicOperation object with no name and acccountName"() {
    setup:
    def input = '''{ "serverGroupName": "testazure-web1-d1-v000", "cloudProvider" : "azure", "appName" : "testazure", "region": "eastus", "credentials": "my-azure-account" }'''

    when:
    DestroyAzureServerGroupAtomicOperation operation = converter.convertOperation(mapper.readValue(input, Map))
    EnableDisableDestroyAzureServerGroupDescription description = converter.convertDescription(mapper.readValue(input, Map))

    then:
    operation
    description.serverGroupName == "testazure-web1-d1-v000"
    description.accountName == "my-azure-account"
    description.region == "eastus"
  }
}
