/*
 * Copyright 2020 Netflix, Inc.
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
 */
package com.netflix.spinnaker.front50.model.plugins;

import static java.lang.String.format;

import com.netflix.spinnaker.front50.config.PluginVersionCleanupProperties;
import com.netflix.spinnaker.moniker.Namer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

/** Responsible for cleaning up old server group plugin version records. */
public class PluginVersionCleanupAgent implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(PluginVersionCleanupAgent.class);

  private final PluginVersionPinningRepository repository;
  private final PluginVersionCleanupProperties properties;
  private final Namer namer;
  private final TaskScheduler taskScheduler;

  public PluginVersionCleanupAgent(
      PluginVersionPinningRepository repository,
      PluginVersionCleanupProperties properties,
      Namer namer,
      TaskScheduler taskScheduler) {
    this.repository = repository;
    this.properties = properties;
    this.namer = namer;
    this.taskScheduler = taskScheduler;
  }

  @PostConstruct
  public void schedule() {
    taskScheduler.scheduleWithFixedDelay(this, properties.interval);
  }

  @Override
  public void run() {
    log.info("Starting cleanup");

    Collection<ServerGroupPluginVersions> allVersions = repository.all();

    // Group all versions by cluster & location (region), then reduce the list by groups that have
    // more than maxVersionsPerCluster, deleting the oldest server group records by created
    // timestamp.
    allVersions.stream()
        .collect(
            Collectors.groupingBy(
                it -> {
                  String clusterName = namer.deriveMoniker(it.getServerGroupName()).getCluster();
                  String group = format("%s-%s", clusterName, it.getLocation());
                  return group;
                }))
        .entrySet()
        .stream()
        .filter(it -> it.getValue().size() > properties.maxVersionsPerCluster)
        .forEach(
            it -> {
              List<String> candidates =
                  it.getValue().stream()
                      .sorted(Comparator.comparing(ServerGroupPluginVersions::getCreateTs))
                      .sorted(Comparator.reverseOrder())
                      .map(ServerGroupPluginVersions::getId)
                      .collect(Collectors.toList());

              List<String> ids =
                  candidates.subList(properties.maxVersionsPerCluster, candidates.size());

              log.debug("Deleting {} version records for '{}'", ids.size(), it.getKey());
              repository.bulkDelete(ids);
            });
  }
}
