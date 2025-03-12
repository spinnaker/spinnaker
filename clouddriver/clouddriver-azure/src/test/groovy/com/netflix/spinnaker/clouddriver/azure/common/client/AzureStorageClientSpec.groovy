/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.common.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.azure.client.AzureStorageClient
import spock.lang.Shared
import spock.lang.Specification

class AzureStorageClientSpec extends Specification{

  @Shared
  ObjectMapper mapper = new ObjectMapper() //.configure(SerializationFeature.INDENT_OUTPUT, true)

  void "getAzureCustomVMImage Simple1"() {
    setup:

    when:
    def customImage = mapper.writeValueAsString(AzureStorageClient.getAzureCustomVMImage(new URI("a.b.c/image1").toString(), "/", "Windows", "westus"))

    then:
    customImage == '''{"name":"image1","uri":"a.b.c/image1","osType":"Windows","region":"westus"}'''
  }

  void "getAzureCustomVMImage Simple2"() {
    setup:

    when:
    def customImage = mapper.writeValueAsString(AzureStorageClient.getAzureCustomVMImage(new URI("https://a.b.c/image1").toString(), "/", "Windows", "westus"))

    then:
    customImage == '''{"name":"image1","uri":"https://a.b.c/image1","osType":"Windows","region":"westus"}'''
  }

  void "getAzureCustomVMImage Simple3"() {
    setup:

    when:
    def customImage = mapper.writeValueAsString(AzureStorageClient.getAzureCustomVMImage(new URI("image1").toString(), ".", "Linux", "eastus"))

    then:
    customImage == '''{"name":"image1","uri":"image1","osType":"Linux","region":"eastus"}'''
  }

}
