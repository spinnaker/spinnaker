/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.controller;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryCluster;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryClusterProvider;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/cloudfoundry/images")
public class CloudFoundryImageController {
  private final CloudFoundryClusterProvider clusterProvider;

  /**
   * Cloud Foundry droplets aren't human readable independently of the server group to which they
   * are attached.
   */
  @RequestMapping(value = "/find", method = RequestMethod.GET)
  public Collection<CloudFoundryCluster> list(@RequestParam(required = false) String account) {
    Stream<CloudFoundryCluster> clusters =
        account == null
            ? clusterProvider.getClusters().values().stream().flatMap(Set::stream)
            : clusterProvider.getClusters().get(account).stream();

    return clusters
        .map(
            cluster ->
                cluster.withServerGroups(
                    cluster.getServerGroups().stream()
                        .filter(serverGroup -> serverGroup.getDroplet() != null)
                        .map(
                            serverGroup ->
                                serverGroup
                                    .withInstances(emptySet())
                                    .withServiceInstances(emptyList()))
                        .collect(toSet())))
        .filter(cluster -> !cluster.getServerGroups().isEmpty())
        .collect(toSet());
  }
}
