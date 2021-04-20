/*
 * Copyright 2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.names;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;

public class EcsServerGroupNameResolver {

  private static final int SEQUENTIAL_NUMBERING_NAMESPACE_SIZE = 1000;

  private static final int MAX_NEXT_SERVER_GROUP_ATTEMPTS = 5;

  private final String ecsClusterName;
  private final AmazonECS ecs;
  private final String region;
  private final Namer<EcsResource> naming;

  public EcsServerGroupNameResolver(
      String ecsClusterName, AmazonECS ecs, String region, Namer<EcsResource> namer) {
    this.ecsClusterName = ecsClusterName;
    this.ecs = ecs;
    this.region = region;
    this.naming = namer;
  }

  public EcsServerGroupName resolveNextName(String application, String stack, String detail) {
    Moniker moniker = Moniker.builder().app(application).detail(detail).stack(stack).build();

    return resolveNextName(moniker);
  }

  public EcsServerGroupName resolveNextName(Moniker currentName) {
    Set<Integer> takenSequences = new HashSet<>();

    // 1. Get a list of all of the services
    List<String> allServices = listAllServices(ecsClusterName);

    // 2. Get the details of the services in the same server group
    List<List<String>> serviceBatches = Lists.partition(allServices, 10);
    for (List<String> serviceBatch : serviceBatches) {
      DescribeServicesRequest request =
          new DescribeServicesRequest()
              .withCluster(ecsClusterName)
              .withServices(serviceBatch)
              .withInclude("TAGS");
      DescribeServicesResult result = ecs.describeServices(request);
      for (Service service : result.getServices()) {
        Moniker moniker = naming.deriveMoniker(new EcsResourceService(service));

        if (isSameName(currentName.getApp(), moniker.getApp())
            && isSameName(currentName.getDetail(), moniker.getDetail())
            && isSameName(currentName.getStack(), moniker.getStack())) {
          takenSequences.add(moniker.getSequence());
        }
      }
    }

    // 3. Find the next free sequence number
    int currentMaxSequence = takenSequences.stream().reduce(Math::max).orElse(0);
    IntStream after = IntStream.range(currentMaxSequence, SEQUENTIAL_NUMBERING_NAMESPACE_SIZE);
    IntStream before = IntStream.range(0, currentMaxSequence);

    Moniker.MonikerBuilder nameBuilder =
        Moniker.builder()
            .app(currentName.getApp())
            .detail(currentName.getDetail())
            .stack(currentName.getStack());

    Moniker newMoniker =
        IntStream.concat(after, before)
            .filter(s -> !takenSequences.contains(s))
            .mapToObj(s -> nameBuilder.sequence(s).build())
            .limit(MAX_NEXT_SERVER_GROUP_ATTEMPTS)
            .filter(moniker -> isNotTaken(moniker))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "All server group names for cluster "
                            + ecsClusterName
                            + " in "
                            + region
                            + " are taken."));

    return new EcsServerGroupName(newMoniker);
  }

  private boolean isSameName(String name, String name2) {
    return (StringUtils.isBlank(name) && StringUtils.isBlank(name2))
        || StringUtils.equals(name, name2);
  }

  private boolean isNotTaken(Moniker newMoniker) {
    String newServiceName = new EcsServerGroupName(newMoniker).getServiceName();

    // An ECS service with this name might exist already in "Draining" state,
    // so it would not show up in the "taken slots" list.
    // We need to describe it to determine if it does exist before using the name
    DescribeServicesRequest request =
        new DescribeServicesRequest().withCluster(ecsClusterName).withServices(newServiceName);
    DescribeServicesResult result = ecs.describeServices(request);

    // an active or draining ECS service with this name was not found
    return result.getServices().isEmpty()
        || result.getServices().get(0).getStatus().equals("INACTIVE");
  }

  private List<String> listAllServices(String ecsClusterName) {
    List<String> allServices = new ArrayList<>();
    String nextToken = null;
    do {
      ListServicesRequest request = new ListServicesRequest().withCluster(ecsClusterName);
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      ListServicesResult result = ecs.listServices(request);
      for (String serviceArn : result.getServiceArns()) {
        allServices.add(serviceArn);
      }

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);
    return allServices;
  }
}
