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
import com.netflix.spinnaker.clouddriver.ecs.model.EcsSubnet;
import com.netflix.spinnaker.clouddriver.model.SubnetProvider;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Exposes the same subnet view as {@link EcsSubnetProvider} under the {@code ecs-native} cloud
 * provider id. See {@link EcsNativeServerClusterProvider} for why this delegate is needed: {@code
 * SubnetController} selects a {@link SubnetProvider} by an exact match on {@link
 * #getCloudProvider()}. Pure delegate; the original is unchanged.
 */
@Component
public class EcsNativeSubnetProvider implements SubnetProvider<EcsSubnet> {

  private final EcsSubnetProvider delegate;

  @Autowired
  public EcsNativeSubnetProvider(EcsSubnetProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getCloudProvider() {
    return EcsNativeCloudProvider.ID;
  }

  @Override
  public Set<EcsSubnet> getAll() {
    return delegate.getAll();
  }
}
