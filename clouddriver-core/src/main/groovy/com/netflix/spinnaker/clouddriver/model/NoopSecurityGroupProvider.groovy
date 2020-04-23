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

class NoopSecurityGroupProvider implements SecurityGroupProvider {

  final String cloudProvider = 'noop'

  @Override
  Set<SecurityGroup> getAll(boolean includeRules) {
    Collections.emptySet()
  }

  @Override
  Set<SecurityGroup> getAllByRegion(boolean includeRules, String region) {
    Collections.emptySet()
  }

  @Override
  Set<SecurityGroup> getAllByAccount(boolean includeRules, String account) {
    Collections.emptySet()
  }

  @Override
  Set<SecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    Collections.emptySet()
  }

  @Override
  Set<SecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    Collections.emptySet()
  }

  @Override
  SecurityGroup get(String account, String region, String name, String vpcId) {
    null
  }

  @Override
  SecurityGroup getById(String account, String region, String id, String vpcId) {
    null
  }
}
