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

import com.netflix.spinnaker.igor.artifacts.ArtifactExtractor
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.service.ArtifactDecorator
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.service.BuildProperties
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.service.BuildQueueOperations
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping
import retrofit.RetrofitError
import retrofit.http.Query

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

    BuildController(BuildServices buildServices,
                    Optional<BuildArtifactFilter> buildArtifactFilter,
                    Optional<ArtifactDecorator> artifactDecorator,
                    Optional<ArtifactExtractor> artifactExtractor) {
        this.buildServices = buildServices
        this.buildArtifactFilter = buildArtifactFilter.orElse(null)
        this.artifactDecorator = artifactDecorator.orElse(null)
        this.artifactExtractor = artifactExtractor.orElse(null)
    }

    @Nullable
    private GenericBuild jobStatus(BuildOperations buildService, String master, String job, Integer buildNumber) {
        GenericBuild build = buildService.getGenericBuild(job, buildNumber)
        if(!build)
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
        Integer buildNumber, HttpServletRequest request) {
        def job = ((String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split('/').drop(5).join('/')
        def buildService = getBuildService(master)
        return jobStatus(buildService, master, job, buildNumber)
    }

    @RequestMapping(value = '/builds/artifacts/{buildNumber}/{master:.+}/**')
    @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
    List<Artifact> getBuildResults(@PathVariable String master, @PathVariable
        Integer buildNumber, @Query("propertyFile") String propertyFile, HttpServletRequest request) {
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

    @RequestMapping(value = '/builds/queue/{master}/{item}')
    @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
    Object getQueueLocation(@PathVariable String master, @PathVariable int item) {
        def buildService = getBuildService(master)
        if (buildService instanceof BuildQueueOperations) {
          return buildService.getQueuedBuild(String.valueOf(item));
        }
        throw new UnsupportedOperationException(String.format("Queued builds are not supported for build service %s", master))
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
        @PathVariable Integer buildNumber) {

        def buildService = getBuildService(master)
        if (buildService instanceof BuildQueueOperations) {
          buildService.stopQueuedBuild(jobName, queuedBuild, buildNumber)
        }

        // TODO(rz): lol, for real?
        "true"
    }

    @RequestMapping(value = '/masters/{name}/jobs/**', method = RequestMethod.PUT)
    @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'WRITE')")
    String build(
        @PathVariable("name") String master,
        @RequestParam Map<String, String> requestParams, HttpServletRequest request) {
        def job = ((String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split('/').drop(4).join('/')
        return getBuildService(master).triggerBuildWithParameters(job, requestParams)
    }

    @RequestMapping(value = '/builds/properties/{buildNumber}/{fileName}/{master:.+}/**')
    @PreAuthorize("hasPermission(#master, 'BUILD_SERVICE', 'READ')")
    Map<String, Object> getProperties(
        @PathVariable String master,
        @PathVariable Integer buildNumber, @PathVariable
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

    private BuildOperations getBuildService(String master) {
        def buildService = buildServices.getService(master)
        if (buildService == null) {
            throw new NotFoundException("Master '${master}' not found")
        }
        return buildService
    }
}
