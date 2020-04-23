/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model

interface SecurityGroupProvider<T extends SecurityGroup> {

  String getCloudProvider()

  Collection<T> getAll(boolean includeRules)

  Collection<T> getAllByRegion(boolean includeRules, String region)

  Collection<T> getAllByAccount(boolean includeRules, String account)

  Collection<T> getAllByAccountAndName(boolean includeRules, String account, String name)

  Collection<T> getAllByAccountAndRegion(boolean includeRule, String account, String region)

  T get(String account, String region, String name, String vpcId)

  T getById(String account, String region, String id, String vpcId)
}
