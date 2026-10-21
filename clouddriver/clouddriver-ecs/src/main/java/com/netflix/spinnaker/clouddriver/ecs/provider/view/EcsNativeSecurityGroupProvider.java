/*
 * Copyright 2026 Spinnaker contributors.
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

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Exposes the same security-group view as {@link EcsSecurityGroupProvider} under the {@code
 * ecs-native} cloud provider id. See {@link EcsNativeServerClusterProvider} for why this delegate
 * is needed: {@code SecurityGroupController} selects a {@link SecurityGroupProvider} by an exact
 * match on {@link #getCloudProvider()}. Pure delegate; the original is unchanged.
 */
@Component
public class EcsNativeSecurityGroupProvider implements SecurityGroupProvider<EcsSecurityGroup> {

  private final EcsSecurityGroupProvider delegate;

  @Autowired
  public EcsNativeSecurityGroupProvider(EcsSecurityGroupProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getCloudProvider() {
    return EcsNativeCloudProvider.ID;
  }

  @Override
  public Collection<EcsSecurityGroup> getAll(boolean includeRules) {
    return delegate.getAll(includeRules);
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    return delegate.getAllByRegion(includeRules, region);
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    return delegate.getAllByAccount(includeRules, account);
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByAccountAndName(
      boolean includeRules, String account, String name) {
    return delegate.getAllByAccountAndName(includeRules, account, name);
  }

  @Override
  public Collection<EcsSecurityGroup> getAllByAccountAndRegion(
      boolean includeRules, String account, String region) {
    return delegate.getAllByAccountAndRegion(includeRules, account, region);
  }

  @Override
  public EcsSecurityGroup get(String account, String region, String name, String vpcId) {
    return delegate.get(account, region, name, vpcId);
  }

  @Override
  public EcsSecurityGroup getById(String account, String region, String id, String vpcId) {
    return delegate.getById(account, region, id, vpcId);
  }
}
