/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers.servergroup;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.ServiceEvent;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroupEvent;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/applications/{application}/serverGroups/{account}/{serverGroupName}")
public class EcsServerGroupController {

  private final AccountCredentialsProvider accountCredentialsProvider;

  private final AmazonClientProvider amazonClientProvider;

  private final ServiceCacheClient serviceCacheClient;

  private final ServerGroupEventStatusConverter statusConverter;

  @Autowired
  public EcsServerGroupController(
      AccountCredentialsProvider accountCredentialsProvider,
      AmazonClientProvider amazonClientProvider,
      ServiceCacheClient serviceCacheClient,
      ServerGroupEventStatusConverter statusConverter) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
    this.serviceCacheClient = serviceCacheClient;
    this.statusConverter = statusConverter;
  }

  @RequestMapping(value = "/events", method = RequestMethod.GET)
  ResponseEntity getServerGroupEvents(
      @PathVariable String account,
      @PathVariable String serverGroupName,
      @RequestParam(value = "region", required = true) String region) {
    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(account);

    if (!(credentials instanceof NetflixECSCredentials)) {
      return new ResponseEntity(
          String.format("Account %s is not an ECS account", account), HttpStatus.BAD_REQUEST);
    }

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(credentials, region, true);

    Service cachedService =
        serviceCacheClient.getAll(account, region).stream()
            .filter(service -> service.getServiceName().equals(serverGroupName))
            .findFirst()
            .get();

    DescribeServicesResult describeServicesResult =
        ecs.describeServices(
            new DescribeServicesRequest()
                .withServices(serverGroupName)
                .withCluster(cachedService.getClusterArn()));

    if (describeServicesResult.getServices().size() == 0) {
      return new ResponseEntity(
          String.format("Server group %s was not found in account ", serverGroupName, account),
          HttpStatus.NOT_FOUND);
    }

    List<ServiceEvent> rawEvents = describeServicesResult.getServices().get(0).getEvents();

    List<EcsServerGroupEvent> events = new ArrayList<>();

    for (ServiceEvent rawEvent : rawEvents) {
      EcsServerGroupEvent newEvent =
          new EcsServerGroupEvent(
              rawEvent.getMessage(),
              rawEvent.getCreatedAt(),
              rawEvent.getId(),
              statusConverter.inferEventStatus(rawEvent));
      events.add(newEvent);
    }

    return new ResponseEntity(events, HttpStatus.OK);
  }
}
