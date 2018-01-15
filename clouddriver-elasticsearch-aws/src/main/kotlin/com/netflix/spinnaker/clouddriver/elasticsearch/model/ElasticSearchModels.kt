/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.model

data class LocationModel(val type: String, val value: String)

data class AccountModel(val id: String, val name: String)

data class InstanceTypeModel(val type: String)

data class BlockDeviceModel(val type: String)

data class Moniker(val application: String,
                   val stack: String?,
                   val details: String?,
                   val cluster: String)

data class ServerGroupModel(val id: String,
                            val name: String,
                            val moniker: Moniker,
                            val location: LocationModel,
                            val account: AccountModel,
                            val instanceTypes: Collection<InstanceTypeModel>,
                            val blockDevice: BlockDeviceModel)
