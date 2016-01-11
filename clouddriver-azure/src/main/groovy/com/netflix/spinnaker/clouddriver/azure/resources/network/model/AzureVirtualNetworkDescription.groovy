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

package com.netflix.spinnaker.clouddriver.azure.resources.network.model

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnetDescription

class AzureVirtualNetworkDescription extends AzureResourceOpsDescription {
  String id
  String type
  List<String> addressSpace /* see addressPrefix */
  List<String> dhcpOptions /* see dnsServers */
  String provisioningState
  String resourceGuid
  String etag
  String location
  Map<String, String> tags
  List<String> ipConfigurations
  String networkSecurityGroup
  String routeTable
  List<AzureSubnetDescription> subnets
}
