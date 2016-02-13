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

package com.netflix.spinnaker.clouddriver.azure.common

import spock.lang.Specification

class AzureUtilitiesSpec extends Specification {
  def "CompareIpv4AddrPrefixes == 0"() {
    expect:
    AzureUtilities.compareIpv4AddrPrefixes(left, right) == 0

    where:
    left                  | right
    '255.255.255.254/31'  | '255.255.255.254/31'
    '0.0.0.0/24'          | '0.0.0.0/24'
    '10.0.0.1/24'         | '10.0.0.2/24'
  }

  def "CompareIpv4AddrPrefixes > 0"() {
    expect:
    AzureUtilities.compareIpv4AddrPrefixes(left, right) > 0

    where:
    left                  | right
    '10.0.2.0/24'         | '10.0.1.0/24'
    '255.255.255.0/24'    | '10.0.1.0/24'
    '255.255.255.0/24'    | '255.255.254.0/24'
  }

  def "CompareIpv4AddrPrefixes < 0"() {
    expect:
    AzureUtilities.compareIpv4AddrPrefixes(left, right) < 0

    where:
    left                  | right
    '10.0.1.0/24'         | '10.0.2.0/24'
    '10.0.1.0/24'         | '255.255.255.0/24'
    '255.255.254.0/24'    | '255.255.255.0/24'
  }

  def "CompareIpv4AddrPrefixes => IllegalArgumentException"() {
    when:
    AzureUtilities.compareIpv4AddrPrefixes(left, right)

    then:
    thrown(IllegalArgumentException)

    where:
    left                  | right
    ''                    | '10.0.1.0/24'
    '10.0.1.0/24'         | ''
    '256.0.1.0/24'        | '10.0.1.0/24'
    '10.0.1.0/24'         | '10.0.256.0/24'
    '10.0.1.0/33'         | '10.0.1.0/24'
    '10.0.1.0/24'         | '10.0.1.0/33'
  }

  def "GetNextSubnet"() {
    expect:
    AzureUtilities.getNextSubnet(vnet, subnet) == next

    where:
    vnet                  | subnet                | next
    '10.0.0.0/16'         | '10.0.1.0/24'         | '10.0.2.0/24'
    '10.0.0.0/8'          | '10.0.255.0/24'       | '10.1.0.0/24'
    '10.0.0.0/8'          | '10.1.0.0/16'         | '10.2.0.0/16'
    '10.0.0.0/16'         | '10.0.0.0/24'         | '10.0.1.0/24'
    '128.0.0.0/1'         | '128.255.0.0/16'      | '129.0.0.0/16'
    '10.0.0.0/16'         | ''                    | '10.0.1.0/24'
    '10.0.0.0/16'         | null                  | '10.0.1.0/24'

  }

  def "GetNextSubnet => IllegalArgumentException"() {
    when:
    AzureUtilities.getNextSubnet(vnet, subnet)

    then:
    thrown(IllegalArgumentException)

    where:
    vnet                  | subnet
    ''                    | '10.0.1.0/24'
    '256.0.1.0/24'        | '10.0.1.0/24'
    '10.0.0.0/16'         | '10.0.256.0/24'
    '10.0.0.0/33'         | '10.0.1.0/24'
    '10.0.0.0/24'         | '10.0.1.0/33'
    '10.0.0.0/16'         | '10.1.1.0/24'
  }

  def "GetNextSubnet => Overflow"() {
    when:
    AzureUtilities.getNextSubnet(vnet, subnet)

    then:
    thrown(ArithmeticException)

    where:
    vnet                  | subnet
    '10.0.0.0/16'         | '10.0.255.0/24'
    '10.0.0.0/8'          | '10.255.0.0/16'
    '10.0.0.0/8'          | '10.255.255.0/24'
    '255.0.0.0/8'         | '255.255.255.0/24'
    '254.255.255.0/24'    | '254.255.255.0/24'
  }

  def "Get Azure REST URL"() {
    expect:
    AzureUtilities.getAzureRESTUrl(subscriptionId, baseUrl, targetUrl, qParams) == url

    where:
    subscriptionId  | baseUrl                         | targetUrl                                                                              | qParams                    |  url
    '12345'         | 'https://management.azure.com'  | 'resourceGroups/rg1/providers/Microsoft.Resources/deployments/deploy12345/operations'  | ['api-version=2015-11-01'] | 'https://management.azure.com/subscriptions/12345/resourceGroups/rg1/providers/Microsoft.Resources/deployments/deploy12345/operations?api-version=2015-11-01'
    '12345'         | 'https://management.azure.com/' | '/resourceGroups/rg1/providers/Microsoft.Resources/deployments/deploy12345/operations' | ['api-version=2015-11-01'] | 'https://management.azure.com/subscriptions/12345/resourceGroups/rg1/providers/Microsoft.Resources/deployments/deploy12345/operations?api-version=2015-11-01'
    '1'             | 'a.b.com'                       | '/c/d/e'                                                                               | ['1=1', '2=2']             | 'a.b.com/subscriptions/1/c/d/e?1=1&2=2'
    '1'             | 'a.b.com'                       | '/c/d/e'                                                                               | []                         | 'a.b.com/subscriptions/1/c/d/e'
    null            | 'a.b.com'                       | '/c/d/e'                                                                               | ['1=1', '2=2']             | 'a.b.com/subscriptions//c/d/e?1=1&2=2'
    null            | 'a.b.com'                       | '/c/d/e'                                                                               | ['1 1', '2=2']             | 'a.b.com/subscriptions//c/d/e?1%201&2=2'
    null            | null                            | null                                                                                   | null                       | 'null/subscriptions/null'
  }
}
