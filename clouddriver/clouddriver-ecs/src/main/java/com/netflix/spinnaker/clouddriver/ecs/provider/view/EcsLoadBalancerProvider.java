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

import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsTargetGroupCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancer;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerDetail;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerSummary;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsTargetGroup;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;

@Component
public class EcsLoadBalancerProvider implements LoadBalancerProvider<EcsLoadBalancer> {

  private final EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient;
  private final EcsAccountMapper ecsAccountMapper;
  private final ServiceCacheClient ecsServiceCacheClient;
  private final EcsTargetGroupCacheClient ecsTargetGroupCacheClient;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public EcsLoadBalancerProvider(
      EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient,
      EcsAccountMapper ecsAccountMapper,
      ServiceCacheClient ecsServiceCacheClient,
      EcsTargetGroupCacheClient ecsTargetGroupCacheClient) {
    this.ecsLoadbalancerCacheClient = ecsLoadbalancerCacheClient;
    this.ecsAccountMapper = ecsAccountMapper;
    this.ecsServiceCacheClient = ecsServiceCacheClient;
    this.ecsTargetGroupCacheClient = ecsTargetGroupCacheClient;
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public List<Item> list() {
    Map<String, EcsLoadBalancerSummary> map = new HashMap<>();
    List<EcsLoadBalancerCache> loadBalancers = ecsLoadbalancerCacheClient.findAll();

    for (EcsLoadBalancerCache lb : loadBalancers) {
      if (lb.getAccount() == null) {
        continue;
      }

      String name = lb.getLoadBalancerName();
      String region = lb.getRegion();

      EcsLoadBalancerSummary summary = map.get(name);
      if (summary == null) {
        summary = new EcsLoadBalancerSummary().withName(name);
        map.put(name, summary);
      }

      EcsLoadBalancerDetail loadBalancer = new EcsLoadBalancerDetail();
      loadBalancer.setAccount(lb.getAccount());
      loadBalancer.setRegion(region);
      loadBalancer.setName(name);
      loadBalancer.setVpcId(lb.getVpcId());
      loadBalancer.setSecurityGroups(lb.getSecurityGroups());
      loadBalancer.setLoadBalancerType(lb.getLoadBalancerType());
      loadBalancer.setTargetGroups(lb.getTargetGroups());

      summary
          .getOrCreateAccount(lb.getAccount())
          .getOrCreateRegion(region)
          .getLoadBalancers()
          .add(loadBalancer);
    }

    return new ArrayList<>(map.values());
  }

  @Override
  public Item get(String name) {
    return null; // intentionally null, implement if/when needed in Deck.
  }

  @Override
  public List<Details> byAccountAndRegionAndName(String account, String region, String name) {
    return null; // intentionally null, implement if/when needed in Deck.
  }

  @Override
  public Set<EcsLoadBalancer> getApplicationLoadBalancers(String application) {
    // Find the load balancers currently in use by ECS services in this application
    Collection<Service> candidates;
    if (application != null && ecsServiceCacheClient.canFindByApplication()) {
      candidates = ecsServiceCacheClient.findByApplication(application);
    } else {
      // Name-prefix glob only: with the tag naming strategy the application comes from the service
      // tags, so the name is not guaranteed to be "app-*". The cached application name filters
      // below.
      String glob =
          application != null
              ? Keys.getServiceKey("*", "*", application + "*")
              : Keys.getServiceKey("*", "*", "*");
      candidates = ecsServiceCacheClient.getAll(ecsServiceCacheClient.filterIdentifiers(glob));
    }
    Set<Service> services =
        candidates.stream()
            .filter(service -> service.getApplicationName().equals(application))
            .collect(Collectors.toSet());
    log.debug("Retrieved {} services for application '{}'", services.size(), application);

    Map<String, Set<String>> targetGroupToServicesMap = new HashMap<>();
    Set<String> targetGroupKeys = new HashSet<>();

    // find all the target group cache keys
    for (Service service : services) {
      String awsAccountName =
          ecsAccountMapper.fromEcsAccountNameToAwsAccountName(service.getAccount());
      for (LoadBalancer loadBalancer : service.getLoadBalancers()) {
        if (loadBalancer.targetGroupArn() != null) {
          String tgArn = loadBalancer.targetGroupArn();
          Set<String> matchingKeys =
              new HashSet<>(
                  ecsTargetGroupCacheClient.findKeys(
                      awsAccountName,
                      service.getRegion(),
                      ArnUtils.extractTargetGroupName(tgArn).get()));
          targetGroupKeys.addAll(matchingKeys);
          // associate target group with services it contains targets for
          if (targetGroupToServicesMap.containsKey(tgArn)) {
            log.debug("Mapping additional service '{}' to '{}'", service.getServiceName(), tgArn);
            Set<String> serviceList = targetGroupToServicesMap.get(tgArn);
            serviceList.add(service.getServiceName());
            targetGroupToServicesMap.put(tgArn, serviceList);
          } else {
            log.debug("Mapping service '{}' to '{}'", service.getServiceName(), tgArn);
            Set<String> srcServices = Sets.newHashSet(service.getServiceName());
            targetGroupToServicesMap.put(tgArn, srcServices);
          }
        }
      }
    }

    // retrieve matching target groups
    List<EcsTargetGroup> tgs = ecsTargetGroupCacheClient.find(targetGroupKeys);

    // find the load balancers for those target groups
    List<EcsLoadBalancerCache> tgLBs =
        ecsLoadbalancerCacheClient.findWithTargetGroups(targetGroupKeys);
    log.debug(
        "Retrieved {} load balancers for {} target group keys.",
        tgLBs.size(),
        targetGroupKeys.size());

    Set<EcsLoadBalancer> ecsLoadBalancers = new HashSet<>();
    for (EcsLoadBalancerCache loadBalancerCache : tgLBs) {
      List<EcsTargetGroup> matchingTGs =
          tgs.stream()
              .filter(tg -> loadBalancerCache.getTargetGroups().contains(tg.getTargetGroupName()))
              .collect(Collectors.toList());
      EcsLoadBalancer ecsLB =
          makeEcsLoadBalancer(loadBalancerCache, matchingTGs, targetGroupToServicesMap);
      ecsLoadBalancers.add(ecsLB);
    }

    return ecsLoadBalancers;
  }

  private EcsLoadBalancer makeEcsLoadBalancer(
      EcsLoadBalancerCache elbCacheData,
      List<EcsTargetGroup> tgCacheData,
      Map<String, Set<String>> tgToServiceMap) {
    EcsLoadBalancer ecsLoadBalancer = new EcsLoadBalancer();
    ecsLoadBalancer.setAccount(elbCacheData.getAccount());
    ecsLoadBalancer.setRegion(elbCacheData.getRegion());
    ecsLoadBalancer.setLoadBalancerArn(elbCacheData.getLoadBalancerArn());
    ecsLoadBalancer.setLoadBalancerName(elbCacheData.getLoadBalancerName());
    ecsLoadBalancer.setLoadBalancerType(elbCacheData.getLoadBalancerType());
    ecsLoadBalancer.setCloudProvider(elbCacheData.getCloudProvider());
    ecsLoadBalancer.setListeners(elbCacheData.getListeners());
    ecsLoadBalancer.setAvailabilityZones(elbCacheData.getAvailabilityZones());
    ecsLoadBalancer.setIpAddressType(elbCacheData.getIpAddressType());
    ecsLoadBalancer.setDnsname(elbCacheData.getDnsname());
    ecsLoadBalancer.setVpcId(elbCacheData.getVpcId());
    ecsLoadBalancer.setCreatedTime(elbCacheData.getCreatedTime());
    ecsLoadBalancer.setSecurityGroups(elbCacheData.getSecurityGroups());
    ecsLoadBalancer.setSubnets(elbCacheData.getSubnets());
    ecsLoadBalancer.setTargetGroups(tgCacheData);
    ecsLoadBalancer.setTargetGroupServices(tgToServiceMap);
    // TODO: get, add target healths per service/tg

    return ecsLoadBalancer;
  }
}
