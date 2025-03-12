/*
 * Copyright 2021 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthResult;
import com.amazonaws.services.elasticloadbalancing.model.InstanceState;
import com.google.common.collect.Iterables;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class AmazonLoadBalancerInstanceStateCachingAgentTest {
  private static final String region = "region";
  private static final String accountName = "accountName";
  private static final String accountId = "accountId";

  @Mock private ProviderCache providerCache;

  @Mock private NetflixAmazonCredentials creds;

  @Mock private AmazonElasticLoadBalancing loadBalancing;

  @Mock private Cache cache;

  @Mock private ApplicationContext ctx;

  private AmazonLoadBalancerInstanceStateCachingAgent getAgent() {
    when(creds.getName()).thenReturn(accountName);
    AmazonClientProvider acp = mock(AmazonClientProvider.class);
    when(acp.getAmazonElasticLoadBalancing(creds, region)).thenReturn(loadBalancing);
    return new AmazonLoadBalancerInstanceStateCachingAgent(
        acp, creds, region, AmazonObjectMapperConfigurer.createConfigured(), ctx);
  }

  @SuppressWarnings("unchecked")
  @Test
  void twoLoadBalancersWithTheSameInstance() {
    // given
    String vpcId = "vpc-11223344556677889";
    String loadBalancerOneName = "lbOneName";
    String loadBalancerTwoName = "lbTwoName";

    // One instance registered with two load balancers is enough for this test.
    // We don't need multiple instances.  We don't even need different opinions
    // of instance state.  Two different load balancers reporting the same state
    // is enough.
    String instanceId = "instanceId";
    String instanceStateString = "instanceState";
    String reasonCode = "reasonCode";
    String description = "description";

    InstanceState instanceState =
        new InstanceState()
            .withInstanceId(instanceId)
            .withState(instanceStateString)
            .withReasonCode(reasonCode)
            .withDescription(description);

    AmazonLoadBalancerInstanceStateCachingAgent agent = getAgent();

    // and
    when(ctx.getBean(Cache.class)).thenReturn(cache);
    when(cache.filterIdentifiers(eq(LOAD_BALANCERS.ns), anyString()))
        .thenReturn(
            List.of(
                Keys.getLoadBalancerKey(loadBalancerOneName, accountId, region, vpcId, "classic"),
                Keys.getLoadBalancerKey(loadBalancerTwoName, accountId, region, vpcId, "classic")),
            List.of()); // nonvpc

    when(loadBalancing.describeInstanceHealth(any(DescribeInstanceHealthRequest.class)))
        .thenReturn(new DescribeInstanceHealthResult().withInstanceStates(instanceState));

    // when
    CacheResult result = agent.loadData(providerCache);

    // then
    verify(ctx).getBean(Cache.class);
    verify(cache, times(2)).filterIdentifiers(eq(LOAD_BALANCERS.ns), anyString());
    verify(loadBalancing, times(2))
        .describeInstanceHealth(any(DescribeInstanceHealthRequest.class));

    // and: 'there's one health item in the cache result'
    assertThat(result.getCacheResults().get(HEALTH.ns)).hasSize(1);

    // and: 'the health item has information from the last load balancer'
    Map<String, Object> healthAttributes =
        Iterables.getOnlyElement(result.getCacheResults().get(HEALTH.ns)).getAttributes();
    assertThat(healthAttributes.get("loadBalancers")).isInstanceOf(List.class);
    List<?> loadBalancers = (List<?>) healthAttributes.get("loadBalancers");
    assertThat(loadBalancers).hasSize(1);
    List<String> loadBalancerNames =
        loadBalancers.stream()
            .map(loadBalancer -> ((Map<String, String>) loadBalancer).get("loadBalancerName"))
            .collect(Collectors.toList());

    assertThat(loadBalancerNames).containsAll(List.of(loadBalancerTwoName));
  }
}
