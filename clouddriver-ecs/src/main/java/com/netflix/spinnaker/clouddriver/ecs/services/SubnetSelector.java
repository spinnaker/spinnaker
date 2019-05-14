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

package com.netflix.spinnaker.clouddriver.ecs.services;

import com.netflix.spinnaker.clouddriver.aws.cache.Keys;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonSubnet;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonSubnetProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSubnet;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.AmazonPrimitiveConverter;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubnetSelector {

  AmazonSubnetProvider amazonSubnetProvider;
  AmazonPrimitiveConverter converter;
  EcsAccountMapper ecsAccountMapper;

  @Autowired
  public SubnetSelector(
      AmazonSubnetProvider amazonSubnetProvider,
      AmazonPrimitiveConverter converter,
      EcsAccountMapper ecsAccountMapper) {
    this.amazonSubnetProvider = amazonSubnetProvider;
    this.converter = converter;
    this.ecsAccountMapper = ecsAccountMapper;
  }

  public Collection<String> resolveSubnetsIds(
      String ecsAccountName, String region, String subnetType) {
    String correspondingAwsAccountName =
        ecsAccountMapper.fromEcsAccountNameToAws(ecsAccountName).getName();

    Set<AmazonSubnet> amazonSubnets =
        amazonSubnetProvider.getAllMatchingKeyPattern(
            Keys.getSubnetKey("*", region, correspondingAwsAccountName));

    Set<EcsSubnet> ecsSubnets = converter.convertToEcsSubnet(amazonSubnets);

    Set<String> filteredSubnetIds =
        ecsSubnets.stream()
            .filter(subnet -> subnetType.equals(subnet.getPurpose()))
            .map(AmazonSubnet::getId)
            .collect(Collectors.toSet());

    return filteredSubnetIds;
  }

  public Collection<String> getSubnetVpcIds(
      String ecsAccountName, String region, Collection<String> subnetIds) {
    String correspondingAwsAccountName =
        ecsAccountMapper.fromEcsAccountNameToAws(ecsAccountName).getName();

    Set<String> subnetKeys =
        subnetIds.stream()
            .map(subnetId -> Keys.getSubnetKey(subnetId, region, correspondingAwsAccountName))
            .collect(Collectors.toSet());
    Set<AmazonSubnet> amazonSubnets = amazonSubnetProvider.loadResults(subnetKeys);

    Set<EcsSubnet> ecsSubnets = converter.convertToEcsSubnet(amazonSubnets);

    return ecsSubnets.stream().map(AmazonSubnet::getVpcId).collect(Collectors.toSet());
  }
}
