/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.pipeline.util.RegionCollector
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@Slf4j
class FindImageFromClusterTask implements CloudProviderAware, RetryableTask {

  static String SUMMARY_TYPE = "Images"

  final long backoffPeriod = 10000

  @Value('${tasks.find-image-from-cluster-timeout-millis:600000}')
  long timeout

  static enum SelectionStrategy {
    /**
     * Choose the server group with the most instances, falling back to newest in the case of a tie
     */
    LARGEST,

    /**
     * Choose the newest ServerGroup by createdTime
     */
    NEWEST,

    /**
     * Choose the oldest ServerGroup by createdTime
     */
    OLDEST,

    /**
     * Fail if there is more than one server group to choose from
     */
    FAIL
  }

  @Value('${find-image.default-resolve-missing-locations:false}')
  boolean defaultResolveMissingLocations = false

  @Value('${default.bake.account:default}')
  String defaultBakeAccount

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  RegionCollector regionCollector

  @Canonical
  static class FindImageConfiguration {
    String cluster
    Moniker moniker
    List<String> regions
    List<String> zones
    List<String> namespaces
    Boolean onlyEnabled = true
    Boolean skipRegionDetection = false
    Boolean resolveMissingLocations
    SelectionStrategy selectionStrategy = SelectionStrategy.NEWEST
    String imageNamePattern

    String getApplication() {
      moniker?.app ?: Names.parseName(cluster).app
    }

    Set<Location> getRequiredLocations() {
        return regions?.collect { new Location(Location.Type.REGION, it) } ?:
          zones?.collect { new Location(Location.Type.ZONE, it) } ?:
            namespaces?.collect { new Location(Location.Type.NAMESPACE, it) } ?:
              []
    }
  }

  @Override
  TaskResult execute(StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)
    FindImageConfiguration config = stage.mapTo(FindImageConfiguration)
    if (config.resolveMissingLocations == null) {
      config.resolveMissingLocations = defaultResolveMissingLocations
    }

    List<Location> missingLocations = []
    List<Location> locationsWithMissingImageIds = []

    Set<String> imageNames = []
    Map<Location, String> imageIds = [:]
    Set<String> inferredRegions = new HashSet<>()

    if (cloudProvider == 'aws' && !config.skipRegionDetection) {
      // Supplement config with regions from subsequent deploy/canary stages:
      def deployRegions = regionCollector.getRegionsFromChildStages(stage)

      deployRegions.forEach {
        if (!config.regions.contains(it)) {
          config.regions.add(it)
          inferredRegions.add(it)
          log.info("Inferred and added region ($it) from deploy stage to FindImageFromClusterTask (executionId: ${stage.execution.id})")
        }
      }
    }

    Map<Location, List<Map<String, Object>>> imageSummaries = config.requiredLocations.collectEntries { location ->
      try {
        def lookupResults = Retrofit2SyncCall.execute(oortService.getServerGroupSummary(
          config.application,
          account,
          config.cluster,
          cloudProvider,
          location.value,
          config.selectionStrategy.toString(),
          SUMMARY_TYPE,
          config.onlyEnabled.toString()))
        List<Map<String, Object>> summaries = (List<Map<String, Object>>) lookupResults.summaries
        summaries?.forEach {
          imageNames << (String) it.imageName
          imageIds[location] = (String) it.imageId

          if (!it.imageId) {
            locationsWithMissingImageIds << location
          }
        }
        return [(location): summaries]
      } catch (SpinnakerHttpException spinnakerHttpException) {
        if (spinnakerHttpException.getResponseCode() == 404) {
          Map<String, Object> responseBody = spinnakerHttpException.getResponseBody()
          if (responseBody.error?.contains("target.fail.strategy")){
            throw new IllegalStateException("Multiple possible server groups present in ${location.value}")
          }
          if (config.resolveMissingLocations) {
            missingLocations << location
            return [(location): null]
          }

          throw new IllegalStateException("Could not find cluster '${config.cluster}' for '$account' in '${location.value}'.")
        }
        throw e
      }
    }

    if (!locationsWithMissingImageIds.isEmpty()) {
      // signifies that at least one summary was missing image details, let's retry until we see image details
      log.warn("One or more locations are missing image details (locations: ${locationsWithMissingImageIds*.value}, cluster: ${config.cluster}, account: ${account}, executionId: ${stage.execution.id})")
      return TaskResult.ofStatus(ExecutionStatus.RUNNING)
    }

    if (missingLocations) {
      log.info("Resolving images in missing locations: ${missingLocations.collect({it -> it.value}).join(",")}, executionId ${stage.execution.id}")

      Set<String> searchNames = extractBaseImageNames(imageNames)
      if (searchNames.size() != 1) {
        throw new IllegalStateException("Request to resolve images for missing ${config.requiredLocations.first().pluralType()} requires exactly one image. (Found ${searchNames}, missing locations: ${missingLocations*.value.join(',')})")
      }

      def deploymentDetailTemplate = imageSummaries.find { k, v -> v != null }.value[0]
      if (!deploymentDetailTemplate.image) {
        throw new IllegalStateException("Missing image on ${deploymentDetailTemplate}")
      }

      List<Map> images = Retrofit2SyncCall.execute(oortService.findImage(cloudProvider, searchNames[0] + '*', account, null, null))
      resolveFromBaseImageName(images, missingLocations, imageSummaries, deploymentDetailTemplate, config, stage.execution.id)

      def unresolved = imageSummaries.findResults { it.value == null ? it.key : null }
      if (unresolved) {
        if (cloudProvider == 'aws') {
          // fallback to look it default bake account; the deploy operation will execute the allowLaunchOperation to share
          // the image into the target account
          List<Map> defaultImages = Retrofit2SyncCall.execute(oortService.findImage(cloudProvider, searchNames[0] + '*', defaultBakeAccount, null, null))
          resolveFromBaseImageName(defaultImages, missingLocations, imageSummaries, deploymentDetailTemplate, config, stage.execution.id)
          unresolved = imageSummaries.findResults { it.value == null ? it.key : null }
        }
      }

      if (unresolved) {
        def errorMessage = "Missing image '${searchNames[0]}' in regions: ${unresolved.value}"

        if (unresolved.value.any {it -> inferredRegions.contains(it)}) {
          errorMessage = "Missing image '${searchNames[0]}' in regions: ${unresolved.value}; ${inferredRegions} were inferred from subsequent deploy stages"
        }

        throw new IllegalStateException(errorMessage)
      }
    }

    List<Map> deploymentDetails = imageSummaries.collect { location, summaries ->
      summaries.findResults { summary ->
        if (config.imageNamePattern && !(summary.imageName ==~ config.imageNamePattern)) {
          return null
        }

        def result = [
          ami              : summary.imageId, // TODO(ttomsu): Deprecate and remove this value.
          imageId          : summary.imageId,
          imageName        : summary.imageName,
          cloudProvider    : cloudProvider,
          refId            : stage.refId,
          sourceServerGroup: summary.serverGroupName
        ]

        if (location.type == Location.Type.REGION) {
          result.region = location.value
        } else if (location.type == Location.Type.ZONE) {
          result.zone = location.value
        }

        try {
          result.putAll(summary.image ?: [:])
          result.putAll(summary.buildInfo ?: [:])
        } catch (Exception e) {
          log.error("Unable to merge server group image/build info (summary: ${summary})", e)
        }

        return result
      }
    }.flatten()

    List<Artifact> artifacts = imageSummaries.collect { placement, summaries ->
      Artifact artifact = Artifact.builder().build()
      summaries.findResults { summary ->
        if (config.imageNamePattern && !(summary.imageName ==~ config.imageNamePattern)) {
          return null
        }
        def location = "global"

        if (placement.type == Location.Type.REGION) {
          location = placement.value
        } else if (placement.type == Location.Type.ZONE) {
          location = placement.value
        }

        def metadata = [
          sourceServerGroup: summary.serverGroupName,
          refId: stage.refId
        ]

        try {
          metadata.putAll(summary.image ?: [:])
          metadata.putAll(summary.buildInfo ?: [:])
        } catch (Exception e) {
          log.error("Unable to merge server group image/build info (summary: ${summary})", e)
        }

        artifact = Artifact.builder()
          .metadata(metadata)
          .name(summary.imageName)
          .location(location)
          .type("${cloudProvider}/image")
          .reference("${cloudProvider}/image")
          .uuid(UUID.randomUUID().toString())
          .build()
      }
      return artifact
    }.flatten()

    Map<String, Object> context = [amiDetails: deploymentDetails, artifacts: artifacts]
    if (cloudProvider == "aws" && config.regions) {
      context.put("regions", config.regions + inferredRegions)
    }
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(
      context
    ).outputs([
      deploymentDetails: deploymentDetails,
      inferredRegions: inferredRegions
    ]).build()
  }

  private void resolveFromBaseImageName(
    List<Map> images,
    ArrayList<Location> missingLocations,
    Map<Location, List<Map<String, Object>>> imageSummaries,
    Map<String, Object> deploymentDetailTemplate,
    FindImageConfiguration config,
    String executionId
  ) {
    for (Map image : images) {
      for (Location location : missingLocations) {
        if (imageSummaries[location] == null && image.amis && image.amis[location.value]) {
          log.info("Resolved missing image in '${location.value}' with '${image.imageName}' (executionId: $executionId)")

          imageSummaries[location] = [
            mkDeploymentDetail((String) image.imageName, (String) image.amis[location.value][0], deploymentDetailTemplate, config)
          ]
        }
        //Docker registry to deployment detail conversion
        else if (imageSummaries[location] == null && image.repository != null && image.tag != null) {
          String imageId = (String) image.repository + ":" + (String) image.tag
          imageSummaries[location] = [
              //In the context of Spinnaker Docker images the imageId and imageName are the same
              mkDeploymentDetail(imageId, imageId, deploymentDetailTemplate, config)
          ]
        }
      }
    }
  }

  private mkDeploymentDetail(String imageName, String imageId, Map deploymentDetailTemplate, FindImageConfiguration config) {
    [
      imageId        : imageId,
      imageName      : imageName,
      serverGroupName: config.cluster,
      image          : deploymentDetailTemplate.image + [imageId: imageId, name: imageName],
      buildInfo      : deploymentDetailTemplate.buildInfo ?: [:]
    ]
  }

  static Set<String> extractBaseImageNames(Collection<String> imageNames) {
    //in the case of two simultaneous bakes, the bakery tacks a counter on the end of the name
    // we want to use the base form of the name, as the search will glob out to the
    def nameCleaner = ~/(.*(?:-ebs|-s3)){1}.*/
    imageNames.findResults {
      def matcher = nameCleaner.matcher(it)
      matcher.matches() ? matcher.group(1) : it
    }.toSet()
  }
}
