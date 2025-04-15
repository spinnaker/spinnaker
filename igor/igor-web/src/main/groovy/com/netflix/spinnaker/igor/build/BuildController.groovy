/*
 * Copyright 2015 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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

package com.netflix.spinnaker.igor.build

import com.google.common.base.Strings
import com.netflix.spinnaker.igor.PendingOperationsCache
import com.netflix.spinnaker.igor.artifacts.ArtifactExtractor
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.UpdatedBuild
import com.netflix.spinnaker.igor.exceptions.BuildJobError
import com.netflix.spinnaker.igor.exceptions.QueuedJobDeterminationError
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.service.ArtifactDecorator
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.service.BuildProperties
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping
import retrofit2.Response
import retrofit2.http.Query

import javax.annotation.Nullable
import javax.servlet.http.HttpServletRequest

import static net.logstash.logback.argument.StructuredArguments.kv
import static org.springframework.http.HttpStatus.NOT_FOUND

@Slf4j
@RestController
class BuildController {
  private BuildServices buildServices
  private BuildArtifactFilter buildArtifactFilter
  private ArtifactDecorator artifactDecorator
  private ArtifactExtractor artifactExtractor
  private PendingOperationsCache pendingOperationsCache

  BuildController(BuildServices buildServices,
                  PendingOperationsCache pendingOperationsCache,
                  Optional<BuildArtifactFilter> buildArtifactFilter,
                  Optional<ArtifactDecorator> artifactDecorator,
                  Optional<ArtifactExtractor> artifactExtractor) {
    this.buildServices = buildServices
    this.pendingOperationsCache = pendingOperationsCache
    this.buildArtifactFilter = buildArtifactFilter.orElse(null)
    this.artifactDecorator = artifactDecorator.orElse(null)
    this.artifactExtractor = artifactExtractor.orElse(null)
  }

  @Nullable
  private GenericBuild jobStatus(BuildOperations buildService, String master, String job, Long buildNumber) {
    GenericBuild build = buildService.getGenericBuild(job, buildNumber)
    if (!build)
      return null

    try {
      build.genericGitRevisions = buildService.getGenericGitRevisions(job, build)
    } catch (Exception e) {
      log.error("could not get scm results for {} / {} / {}", kv("master", master), kv("job", job), kv("buildNumber", buildNumber), e)
    }

    if (artifactDecorator) {
      artifactDecorator.decorate(build)
    }

    if (buildArtifactFilter) {
      build.artifacts = buildArtifactFilter.filterArtifacts(build.artifacts)
    }
    return build
  }

  @RequestMapping(value = '/builds/status/{buildNumber}/{master:.+}/**')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  GenericBuild getJobStatus(@PathVariable String master, @PathVariable
    Long buildNumber, HttpServletRequest request) {
    def job = ((String) request.getAttribute(
      HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split('/').drop(5).join('/')
    def buildService = getBuildService(master)
    return jobStatus(buildService, master, job, buildNumber)
  }

  @RequestMapping(value = '/builds/status/{buildNumber}/{master}')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  GenericBuild getJobStatus(
    @PathVariable String master,
    @PathVariable Long buildNumber,
    @RequestParam("job") String job) {
    def buildService = getBuildService(master)
    return jobStatus(buildService, master, job, buildNumber)
  }

  @RequestMapping(value = '/builds/artifacts/{buildNumber}/{master:.+}/**')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  List<Artifact> getBuildResults(@PathVariable String master, @PathVariable
    Long buildNumber, @Query("propertyFile") String propertyFile, HttpServletRequest request) {
    def job = ((String) request.getAttribute(
      HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split('/').drop(5).join('/')
    def buildService = getBuildService(master)
    GenericBuild build = jobStatus(buildService, master, job, buildNumber)
    if (build && buildService instanceof BuildProperties && artifactExtractor != null) {
      build.properties = buildService.getBuildProperties(job, build, propertyFile)
      return artifactExtractor.extractArtifacts(build)
    }
    return Collections.emptyList()
  }


  @RequestMapping(value = '/builds/artifacts/{buildNumber}/{master}')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  List<Artifact> getBuildResults(@PathVariable String master, @PathVariable
    Long buildNumber, @RequestParam("job") String job ,@Query("propertyFile") String propertyFile) {
    def buildService = getBuildService(master)
    GenericBuild build = jobStatus(buildService, master, job, buildNumber)
    if (build && buildService instanceof BuildProperties && artifactExtractor != null) {
      build.properties = buildService.getBuildProperties(job, build, propertyFile)
      return artifactExtractor.extractArtifacts(build)
    }
    return Collections.emptyList()
  }

  @RequestMapping(value = '/builds/queue/{master}/{item}')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  Object getQueueLocation(@PathVariable String master, @PathVariable long item) {
    def buildService = getBuildService(master)
    return buildService.queuedBuild(master, item)
  }

  @RequestMapping(value = '/builds/all/{master:.+}/**')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  List<Object> getBuilds(@PathVariable String master, HttpServletRequest request) {
    def job = ((String) request.getAttribute(
      HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split('/').drop(4).join('/')
    def buildService = getBuildService(master)
    return buildService.getBuilds(job)
  }

  @RequestMapping(value = "/masters/{name}/jobs/{jobName}/stop/{queuedBuild}/{buildNumber}", method = RequestMethod.PUT)
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'WRITE')")
  String stop(
    @PathVariable("name") String master,
    @PathVariable String jobName,
    @PathVariable String queuedBuild,
    @PathVariable Long buildNumber) {
    stopJob(master, buildNumber, jobName, queuedBuild)
    "true"
  }

  @RequestMapping(value = "/masters/{master}/jobs/stop/{queuedBuild}/{buildNumber}", method = RequestMethod.PUT)
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'WRITE')")
  String stopWithQueryParam(
    @PathVariable String master,
    @RequestParam String jobName,
    @PathVariable String queuedBuild,
    @PathVariable Long buildNumber) {

    stopJob(master, buildNumber, jobName, queuedBuild)
    "true"
  }


  void stopJob(String master, long buildNumber, String jobName, String queuedBuild) {
    def buildService = getBuildService(master)
    if (buildService instanceof JenkinsService) {
      // Jobs that haven't been started yet won't have a buildNumber
      // (They're still in the queue). We use 0 to denote that case
      if (buildNumber != 0 &&
        buildService.metaClass.respondsTo(buildService, 'stopRunningBuild')) {
        buildService.stopRunningBuild(jobName, buildNumber)
      } else {
        // The jenkins api for removing a job from the queue (http://<Jenkins_URL>/queue/cancelItem?id=<queuedBuild>)
        // always returns a 404. This try catch block insures that the exception is eaten instead
        // of being handled by the handleOtherException handler and returning a 500 to orca
        try {
          if (buildService.metaClass.respondsTo(buildService, 'stopQueuedBuild')) {
            buildService.stopQueuedBuild(queuedBuild)
          }
        } catch (SpinnakerHttpException e) {
          if (e.getResponseCode() != NOT_FOUND.value()) {
            throw e
          }
        }
      }
    }

  }

  @RequestMapping(value = "/masters/{name}/jobs/**/update/{buildNumber}", method = RequestMethod.PATCH)
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'WRITE')")
  void update(
    @PathVariable("name") String master,
    @PathVariable("buildNumber") Long buildNumber,
    @RequestBody UpdatedBuild updatedBuild,
    HttpServletRequest request
  ) {
    def jobName = ((String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE))
      .split('/')
      .drop(4)
      .dropRight(2)
      .join('/')

    def buildService = getBuildService(master)
    buildService.updateBuild(jobName, buildNumber, updatedBuild)
  }

  @RequestMapping(value = '/masters/{name}/jobs/**', method = RequestMethod.PUT)
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'WRITE')")
  ResponseEntity<String> build(
    @PathVariable("name") String master,
    @RequestParam Map<String, String> requestParams,
    @RequestBody(required = false) String startTime,
    HttpServletRequest request) {
    def job = ((String) request.getAttribute(
      HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split('/').drop(4).join('/')

    String pendingKey = computePendingBuildKey(master, job, requestParams, startTime)
    // Initializing buildNumber to null will get it silently casted to "null" down the line
    String buildNumber = ""

    PendingOperationsCache.OperationState pendingStatus = pendingOperationsCache.getAndSetOperationStatus(pendingKey, PendingOperationsCache.OperationStatus.PENDING, "")
    if (pendingStatus.status == PendingOperationsCache.OperationStatus.PENDING) {
      log.info("Received duplicate request to the start job {}, status: {}, pendingKey: {}", job,
        pendingStatus.status, pendingKey)
      return ResponseEntity.accepted().build()
    }
    if (pendingStatus.status == PendingOperationsCache.OperationStatus.COMPLETED && !Strings.isNullOrEmpty(pendingStatus.value)) {
      log.info("Received duplicate request to the start job {}, status: {}, pendingKey: {}", job,
        pendingStatus.status, pendingKey)
      pendingOperationsCache.clear(pendingKey)
      return ResponseEntity.of(Optional.of(pendingStatus.value))
    }

    try {
      def buildService = getBuildService(master)
      if (buildService instanceof JenkinsService) {
        Response response
        JenkinsService jenkinsService = (JenkinsService) buildService
        JobConfig jobConfig = jenkinsService.getJobConfig(job)
        if (!jobConfig.buildable) {
          throw new BuildJobError("Job '${job}' is not buildable. It may be disabled.")
        }

        if (jobConfig.parameterDefinitionList?.size() > 0) {
          validateJobParameters(jobConfig, requestParams)
        }
        if (requestParams && jobConfig.parameterDefinitionList?.size() > 0) {
          response = jenkinsService.buildWithParameters(job, requestParams)
        } else if (!requestParams && jobConfig.parameterDefinitionList?.size() > 0) {
          // account for when you just want to fire a job with the default parameter values by adding a dummy param
          response = jenkinsService.buildWithParameters(job, ['startedBy': "igor"])
        } else if (!requestParams && (!jobConfig.parameterDefinitionList || jobConfig.parameterDefinitionList.size() == 0)) {
          response = jenkinsService.build(job)
        } else { // Jenkins will reject the build, so don't even try
          // we should throw a BuildJobError, but I get a bytecode error : java.lang.VerifyError: Bad <init> method call from inside of a branch
          throw new RuntimeException("job : ${job}, passing params to a job which doesn't need them")
        }

        if (response.code() != 201) {
          throw new BuildJobError("Received a non-201 status when submitting job '${job}' to master '${master}'")
        }

        log.info("Submitted build job '{}'", kv("job", job))
        def locationHeader = response.headers().get("location")
        if (!locationHeader) {
          throw new QueuedJobDeterminationError("Could not find Location header for job '${job}'")
        }

        buildNumber = locationHeader.split('/')[-1]
      } else {
        buildNumber = buildService.triggerBuildWithParameters(job, requestParams)
      }
    }
    finally {
      pendingOperationsCache.setOperationStatus(pendingKey, PendingOperationsCache.OperationStatus.COMPLETED, buildNumber)
    }

    return ResponseEntity.of(Optional.of(buildNumber))
  }

  static void validateJobParameters(JobConfig jobConfig, Map<String, String> requestParams) {
    jobConfig.parameterDefinitionList.each { parameterDefinition ->
      String matchingParam = requestParams[parameterDefinition.name]
      if (matchingParam != null &&
        parameterDefinition.type == 'ChoiceParameterDefinition' &&
        parameterDefinition.choices != null &&
        !parameterDefinition.choices.contains(matchingParam)) {
        throw new InvalidJobParameterException("`${matchingParam}` is not a valid choice " +
          "for `${parameterDefinition.name}`. Valid choices are: ${parameterDefinition.choices.join(', ')}")
      }
    }
  }

  static String computePendingBuildKey(String master, String job, Map<String, String> requestParams, String startTime) {
    String key = master + ":" + job + ":" + AuthenticatedRequest.getSpinnakerExecutionId().orElse("NO_EXECUTION_ID")

    if (startTime != null && !startTime.isEmpty()) {
      key = key + ":startTime=" + startTime
    }

    requestParams.each { parameterDefinition ->
      key = key + ":" + parameterDefinition.key + "=" + parameterDefinition.value
    }

    return key
  }

  @RequestMapping(value = '/builds/properties/{buildNumber}/{fileName}/{master:.+}/**')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  Map<String, Object> getProperties(
    @PathVariable String master,
    @PathVariable Long buildNumber, @PathVariable
      String fileName, HttpServletRequest request) {
    def job = ((String) request.getAttribute(
      HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split('/').drop(6).join('/')
    def buildService = getBuildService(master)
    if (buildService instanceof BuildProperties) {
      BuildProperties buildProperties = (BuildProperties) buildService
      def genericBuild = buildService.getGenericBuild(job, buildNumber)
      return buildProperties.getBuildProperties(job, genericBuild, fileName)
    }
    return Collections.emptyMap()
  }


  @RequestMapping(value = '/builds/properties/{buildNumber}/{fileName}/{master}')
  @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
  Map<String, Object> getProperties(
    @PathVariable String master,
    @PathVariable Long buildNumber, @PathVariable
      String fileName, @RequestParam("job") String job) {
    def buildService = getBuildService(master)
    if (buildService instanceof BuildProperties) {
      BuildProperties buildProperties = (BuildProperties) buildService
      def genericBuild = buildService.getGenericBuild(job, buildNumber)
      return buildProperties.getBuildProperties(job, genericBuild, fileName)
    }
    return Collections.emptyMap()
  }

  private BuildOperations getBuildService(String master) {
    def buildService = buildServices.getService(master)
    if (buildService == null) {
      throw new NotFoundException("Master '${master}' not found")
    }
    return buildService
  }

  @InheritConstructors
  static class InvalidJobParameterException extends InvalidRequestException {}

}
