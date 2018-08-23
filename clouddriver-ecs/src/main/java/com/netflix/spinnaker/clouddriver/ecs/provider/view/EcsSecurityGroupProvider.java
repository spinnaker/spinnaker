/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonSecurityGroupProvider;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
class EcsSecurityGroupProvider implements SecurityGroupProvider<EcsSecurityGroup> {

  final String cloudProvider = EcsCloudProvider.ID;

  final AmazonSecurityGroupProvider amazonSecurityGroupProvider;

  final AmazonPrimitiveConverter amazonPrimitiveConverter;

  @Autowired
  EcsSecurityGroupProvider(AmazonPrimitiveConverter amazonPrimitiveConverter,
                           AmazonSecurityGroupProvider amazonSecurityGroupProvider) {
    this.amazonPrimitiveConverter = amazonPrimitiveConverter;
    this.amazonSecurityGroupProvider = amazonSecurityGroupProvider;
  }

  @Override
  public Collection<EcsSecurityGroup> getAll(boolean includeRules) {
    return amazonPrimitiveConverter.convertToEcsSecurityGroup(amazonSecurityGroupProvider.getAll(includeRules));
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    return amazonPrimitiveConverter.convertToEcsSecurityGroup(amazonSecurityGroupProvider.getAllByRegion(includeRules, region));
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    return amazonPrimitiveConverter.convertToEcsSecurityGroup(amazonSecurityGroupProvider.getAllByAccount(includeRules, account));
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    return amazonPrimitiveConverter.convertToEcsSecurityGroup(amazonSecurityGroupProvider.getAllByAccountAndName(includeRules, account, name));
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    return amazonPrimitiveConverter.convertToEcsSecurityGroup(amazonSecurityGroupProvider.getAllByAccountAndRegion(includeRules, account, region));
  }

  @Override
  public EcsSecurityGroup get(String account, String region, String name, String vpcId) {
    return amazonPrimitiveConverter.convertToEcsSecurityGroup(amazonSecurityGroupProvider.get(account, region, name, vpcId));
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

}
