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

import com.netflix.spinnaker.clouddriver.aws.model.Role;
import com.netflix.spinnaker.clouddriver.aws.model.RoleProvider;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Exposes the same IAM role view as {@link EcsRoleProvider} under the {@code ecs-native} cloud
 * provider id. See {@link EcsNativeServerClusterProvider} for why this delegate is needed: {@code
 * RoleController} selects a {@link RoleProvider} by an exact match on {@link #getCloudProvider()}.
 * Pure delegate; the original is unchanged.
 */
@Component
public class EcsNativeRoleProvider implements RoleProvider {

  private final EcsRoleProvider delegate;

  @Autowired
  public EcsNativeRoleProvider(EcsRoleProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getCloudProvider() {
    return EcsNativeCloudProvider.ID;
  }

  @Override
  public Collection<? extends Role> getAll() {
    return delegate.getAll();
  }
}
