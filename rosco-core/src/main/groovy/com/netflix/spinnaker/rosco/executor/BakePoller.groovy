/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.executor

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.jobs.BakeRecipe
import com.netflix.spinnaker.rosco.jobs.JobExecutor
import com.netflix.spinnaker.rosco.persistence.BakeStore
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

/**
 * BakePoller periodically queries the bake store for incomplete bakes. For each incomplete bake, it queries
 * the job executor for an up-to-date status and logs. The status and logs are then persisted via the bake
 * store. When a bake completes, it is the BakePoller that persists the completed bake details via the bake store.
 * The polling interval defaults to 15 seconds and can be overridden by specifying the
 * rosco.polling.pollingIntervalSeconds property.
 */
@Slf4j
@Component
class BakePoller implements ApplicationListener<ContextRefreshedEvent> {

  @Autowired
  String roscoInstanceId

  @Value('${rosco.polling.pollingIntervalSeconds:15}')
  int pollingIntervalSeconds

  @Value('${rosco.polling.orphanedJobPollingIntervalSeconds:30}')
  int orphanedJobPollingIntervalSeconds

  @Value('${rosco.polling.orphanedJobTimeoutMinutes:30}')
  long orphanedJobTimeoutMinutes

  @Autowired
  BakeStore bakeStore

  @Autowired
  JobExecutor executor

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  Registry registry

  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    log.info("Starting polling agent for rosco instance $roscoInstanceId...")

    // Update this rosco instance's incomplete bakes.
    Schedulers.io().createWorker().schedulePeriodically(
      {
        try {
          rx.Observable.from(bakeStore.thisInstanceIncompleteBakeIds)
            .subscribe(
              { String incompleteBakeId ->
                try {
                  updateBakeStatusAndLogs(incompleteBakeId)
                } catch (Exception e) {
                  log.error("Update Polling Error:", e)
                }
              },
              {
                log.error("Update Error: ${it.message}")
              },
              {} as Action0
            )
        } catch (Exception e) {
          log.error("Update Polling Error:", e)
        }
      } as Action0, 0, pollingIntervalSeconds, TimeUnit.SECONDS
    )

    // Check _all_ rosco instances' incomplete bakes for staleness.
    Schedulers.io().createWorker().schedulePeriodically(
      {
        try {
          rx.Observable.from(bakeStore.allIncompleteBakeIds.entrySet())
            .subscribe(
              { Map.Entry<String, Set<String>> entry ->
                String roscoInstanceId = entry.key
                Set<String> incompleteBakeIds = entry.value

                if (roscoInstanceId != this.roscoInstanceId) {
                  try {
                    rx.Observable.from(incompleteBakeIds)
                      .subscribe(
                        { String statusId ->
                          BakeStatus bakeStatus = bakeStore.retrieveBakeStatusById(statusId)

                          // The updatedTimestamp key will not be present if the in-flight bake is managed by an
                          // older-style (i.e. rosco/rush) rosco instance.
                          if (bakeStatus?.updatedTimestamp) {
                            long currentTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(bakeStore.timeInMilliseconds)
                            long lastUpdatedTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(bakeStatus.updatedTimestamp)
                            long eTimeMinutes = TimeUnit.SECONDS.toMinutes(currentTimeSeconds - lastUpdatedTimeSeconds)

                            // This can only be true if the rosco instance that owns this bake has not been updating the status.
                            // Note that this only applies to bakes owned by _other_ rosco instances, not _this_ one.
                            if (eTimeMinutes >= orphanedJobTimeoutMinutes) {
                              BakeRequest bakeRequest = bakeStore.retrieveBakeRequestById(statusId)
                              log.info("The staleness of bake $statusId ($eTimeMinutes minutes) has met or exceeded the " +
                                       "value of orphanedJobTimeoutMinutes ($orphanedJobTimeoutMinutes minutes)" +
                                       "(executionId: $bakeRequest.spinnaker_execution_id).")

                              boolean cancellationSucceeded = bakeStore.cancelBakeById(statusId)

                              if (!cancellationSucceeded) {
                                bakeStore.removeFromIncompletes(roscoInstanceId, statusId)
                              }

                              // This will have the most up-to-date timestamp.
                              bakeStatus = bakeStore.retrieveBakeStatusById(statusId)
                              def tags = [success: "false", cause: "orphanTimedOut", region: bakeStore.retrieveRegionById(statusId)]
                              long millis = bakeStatus.updatedTimestamp - bakeStatus.createdTimestamp
                              registry.timer(registry.createId("bakesCompleted", tags)).record(millis, TimeUnit.MILLISECONDS)
                            }
                          }
                        },
                        {
                          log.error("Error: ${it.message}")
                        },
                        {} as Action0
                    )
                  } catch (Exception e) {
                    log.error("Zombie Killer Polling Error:", e)
                  }
                }
              },
              {
                log.error("Zombie Killer Error: ${it.message}")
              },
              {} as Action0
            )
        } catch (Exception e) {
          log.error("Zombie Killer Polling Error:", e)
        }
      } as Action0, 0, orphanedJobPollingIntervalSeconds, TimeUnit.SECONDS
    )
  }

  void updateBakeStatusAndLogs(String statusId) {
    BakeStatus bakeStatus = executor.updateJob(statusId)
    def tags

    if (bakeStatus) {
      if (bakeStatus.state == BakeStatus.State.COMPLETED) {
        completeBake(statusId, bakeStatus.logsContent)
        tags = [success: "true"]
      } else if (bakeStatus.state == BakeStatus.State.CANCELED) {
        tags = [success: "false", cause: "jobFailed"]
      }

      bakeStore.updateBakeStatus(bakeStatus)
    } else {
      String errorMessage = "Unable to retrieve status for '$statusId'."
      log.error(errorMessage)
      bakeStore.storeBakeError(statusId, errorMessage)
      bakeStore.cancelBakeById(statusId)

      tags = [success: "false", cause: "failedToUpdateJob"]
    }

    if (tags) {
      // This will have the most up-to-date timestamp.
      bakeStatus = bakeStore.retrieveBakeStatusById(statusId)

      if (bakeStatus) {
        tags.region = bakeStore.retrieveRegionById(statusId)
        long millis = bakeStatus.updatedTimestamp - bakeStatus.createdTimestamp
        registry.timer(registry.createId("bakesCompleted", tags)).record(millis, TimeUnit.MILLISECONDS)
      }
    }
  }


  void completeBake(String bakeId, String logsContent) {
    if (logsContent) {
      def cloudProvider = bakeStore.retrieveCloudProviderById(bakeId)

      if (cloudProvider) {
        def cloudProviderType = BakeRequest.CloudProviderType.valueOf(cloudProvider)
        def cloudProviderBakeHandler = cloudProviderBakeHandlerRegistry.lookup(cloudProviderType)

        if (cloudProviderBakeHandler) {
          String region = bakeStore.retrieveRegionById(bakeId)
          cloudProviderBakeHandler.deleteArtifactFile(bakeId)

          if (region) {
            Bake bakeDetails = cloudProviderBakeHandler.scrapeCompletedBakeResults(region, bakeId, logsContent)

            if (bakeDetails) {
              BakeRequest bakeRequest = bakeStore.retrieveBakeRequestById(bakeId)
              BakeRecipe bakeRecipe = bakeStore.retrieveBakeRecipeById(bakeId)
              bakeDetails.artifact = cloudProviderBakeHandler.produceArtifactDecorationFrom(
                bakeRequest, bakeRecipe, bakeDetails, cloudProvider, region
              )
              bakeStore.updateBakeDetails(bakeDetails)
            }

            return
          }
        }
      }
    }

    log.error("Unable to retrieve bake details for '$bakeId'.")
  }
}
