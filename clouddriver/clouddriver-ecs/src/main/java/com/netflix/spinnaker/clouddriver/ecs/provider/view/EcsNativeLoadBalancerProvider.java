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
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Exposes the same ECS load balancer / target group views as {@link EcsLoadBalancerProvider} under
 * the {@code ecs-native} cloud provider id, for the same reason as {@link
 * EcsNativeServerClusterProvider}: read views are matched by an exact string comparison against
 * {@link LoadBalancerProvider#getCloudProvider()}, so without this delegate, load balancer lookups
 * scoped to {@code cloudProvider: "ecs-native"} would silently find nothing even though the
 * underlying ECS target groups are cached and visible under {@code "ecs"}.
 *
 * <p>The provider-scoped lookups ({@link #list()}, {@link #get(String)}, {@link
 * #byAccountAndRegionAndName}) delegate to the original {@link EcsLoadBalancerProvider}, which is
 * unchanged. The application-wide aggregate {@link #getApplicationLoadBalancers(String)}
 * deliberately returns empty: {@code LoadBalancerController}'s {@code
 * /applications/{app}/loadBalancers} flattens every provider's result without de-duplication, so
 * delegating it would list each ECS load balancer twice (the same reasoning as the aggregate
 * cluster listings in {@link EcsNativeServerClusterProvider}).
 */
@Component
public class EcsNativeLoadBalancerProvider implements LoadBalancerProvider<EcsLoadBalancer> {

  private final EcsLoadBalancerProvider delegate;

  @Autowired
  public EcsNativeLoadBalancerProvider(EcsLoadBalancerProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getCloudProvider() {
    return EcsNativeCloudProvider.ID;
  }

  @Override
  public List<? extends Item> list() {
    return delegate.list();
  }

  @Override
  public Item get(String name) {
    return delegate.get(name);
  }

  @Override
  public List<? extends Details> byAccountAndRegionAndName(
      String account, String region, String name) {
    return delegate.byAccountAndRegionAndName(account, region, name);
  }

  @Override
  public Set<EcsLoadBalancer> getApplicationLoadBalancers(String application) {
    // The ecs provider already reports these; see the class Javadoc.
    return Set.of();
  }
}
