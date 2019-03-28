/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker

import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.hystrix.SimpleHystrixCommand
import com.netflix.spinnaker.igor.build.BuildController
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.config.WerckerProperties.WerckerHost
import com.netflix.spinnaker.igor.exceptions.BuildJobError
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.wercker.model.Application
import com.netflix.spinnaker.igor.wercker.model.Pipeline
import com.netflix.spinnaker.igor.wercker.model.QualifiedPipelineName
import com.netflix.spinnaker.igor.wercker.model.Run
import com.netflix.spinnaker.igor.wercker.model.RunPayload
import groovy.util.logging.Slf4j
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray

import static com.netflix.spinnaker.igor.model.BuildServiceProvider.WERCKER
import static net.logstash.logback.argument.StructuredArguments.kv

@Slf4j
class WerckerService implements BuildOperations {

    String groupKey
    WerckerClient werckerClient
    String user
    String token
    String authHeaderValue
    String address
    String master
    WerckerCache cache
    final Permissions permissions

    private static String branch = 'master'
    private static limit = 300

    WerckerService(WerckerHost wercker, WerckerCache cache, WerckerClient werckerClient, Permissions permissions) {
        this.groupKey = wercker.name
        this.werckerClient = werckerClient
        this.user = wercker.user
        this.cache = cache
        this.address = address
        this.master = wercker.name
        this.setToken(token)
        this.address = wercker.address
        this.setToken(wercker.token)
        this.permissions = permissions
    }

    @Override
    String getName() {
        this.groupKey
    }
/**
     * Custom setter for token, in order to re-set the authHeaderValue
     * @param token
     * @return
     */
    void setToken(String token) {
        this.authHeaderValue = 'Bearer ' + token
    }

    @Override
    BuildServiceProvider getBuildServiceProvider() {
        return WERCKER
    }

    @Override
    List<GenericGitRevision> getGenericGitRevisions(final String job, final int buildNumber) {
        return null
    }

    @Override
    GenericBuild getGenericBuild(final String job, final int buildNumber) {
        QualifiedPipelineName qPipeline = QualifiedPipelineName.of(job)
        String runId = cache.getRunID(groupKey, job, buildNumber)
        if (runId == null) {
            throw new BuildJobError(
            "Could not find build number ${buildNumber} for job ${job} - no matching run ID!")
        }
        Run run = getRunById(runId)
        String addr = address.endsWith("/") ? address.substring(0, address.length()-1) : address

        GenericBuild genericBuild = new GenericBuild()
        genericBuild.name = job
        genericBuild.building = true
        genericBuild.fullDisplayName = "Wercker Job " + job + " [" + buildNumber + "]"
        genericBuild.number = buildNumber
        genericBuild.building = (run.finishedAt == null)
        genericBuild.fullDisplayName = "Wercker Job " + job + " [" + runId + "]"
        //The API URL: address + "api/v3/runs/" + cache.getRunID(groupKey, job, buildNumber)
        genericBuild.url = String.join("/", addr, qPipeline.ownerName, qPipeline.appName, "runs",
            qPipeline.pipelineName, runId)
        genericBuild.result = mapRunToResult(run)

        return genericBuild
    }

    Result mapRunToResult(final Run run) {
        if (run.finishedAt == null) return Result.BUILDING
        if ("notstarted".equals(run.status)) return Result.NOT_BUILT
        switch (run.result) {
            case "passed":
                return Result.SUCCESS
                break
            case "aborted":
                return Result.ABORTED
                break
            case "failed":
                return Result.FAILURE
                break
        }
        return Result.UNSTABLE
    }

    Response stopRunningBuild (String appAndPipelineName, Integer buildNumber){
        String runId = cache.getRunID(groupKey, appAndPipelineName, buildNumber)
        if (runId == null) {
            log.warn("Could not cancel build number {} for job {} - no matching run ID!",
                    kv("buildNumber", buildNumber), kv("job", appAndPipelineName))
            return
        }
        log.info("Aborting Wercker run id {}", kv("runId", runId))
        return abortRun(authHeaderValue, runId, [:])
    }

    @Override
    int triggerBuildWithParameters(final String appAndPipelineName, final Map<String, String> queryParameters) {
        QualifiedPipelineName qPipeline = QualifiedPipelineName.of(appAndPipelineName)
        String org = qPipeline.ownerName
        String appName = qPipeline.appName
        String pipelineName = qPipeline.pipelineName

        List<Pipeline> pipelines = getPipelines(org, appName)
        List<String> pnames = pipelines.collect { it.name + "|" +it.pipelineName + "|" + it.id }
        log.debug "triggerBuildWithParameters pipelines: ${pnames}"
        Pipeline pipeline = pipelines.find {p -> pipelineName.equals(p.name)}
        if (pipeline) {
            log.info("Triggering run for pipeline {} with id {}",
                    kv("pipelineName", pipelineName), kv("pipelineId", pipeline.id))
            try {
                Map<String, Object> runInfo = triggerBuild(new RunPayload(pipeline.id, 'Triggered from Spinnaker'))
                //TODO desagar the triggerBuild call above itself returns a Run, but the createdAt date
                //is not in ISO8601 format, and parsing fails. The following is a temporary
                //workaround - the getRunById call below gets the same Run object but Wercker
                //returns the date in the ISO8601 format for this case.
                Run run = getRunById(runInfo.get('id'))

                //Create an entry in the WerckerCache for this new run. This will also generate
                //an integer build number for the run
                Map<String, Integer> runIdBuildNumbers = cache.updateBuildNumbers(
                        master, appAndPipelineName, Collections.singletonList(run))

                log.info("Triggered run {} at URL {} with build number {}",
                        kv("runId", run.id), kv("url", run.url),
                        kv("buildNumber", runIdBuildNumbers.get(run.id)))

                //return the integer build number for this run id
                return runIdBuildNumbers.get(run.id)
            } catch (RetrofitError e) {
                def body = e.getResponse().getBody()
                String wkrMsg
                if (body instanceof TypedByteArray) {
                    wkrMsg = new String(((retrofit.mime.TypedByteArray) body).getBytes())
                } else {
                    wkrMsg = body.in().text
                }
                log.error("Failed to trigger build for pipeline {}. {}", kv("pipelineName", pipelineName), kv("errMsg", wkrMsg))
                throw new BuildJobError(
                "Failed to trigger build for pipeline ${pipelineName}! Error from Wercker is: ${wkrMsg}")
            }
        } else {
            throw new BuildController.InvalidJobParameterException(
            "Could not retrieve pipeline ${pipelineName} for application ${appName} from Wercker!")
        }
    }

    /**
     * Returns List of all Wercker jobs in the format of type/org/app/pipeline
     */
    List<String> getJobs() {
        List<String> jobs = []
        List<Application> apps = getApplications()
        long start = System.currentTimeMillis()
        apps.each { app ->
            try {
                List<Pipeline> pipelines = app.pipelines ?: []
                jobs.addAll( pipelines.collect {
                    it.type + QualifiedPipelineName.SPLITOR +
                        new QualifiedPipelineName(app.owner.name, app.name, it.name).toString()
                } )
            } catch(retrofit.RetrofitError err) {
                log.error "Error getting pipelines for ${app.owner.name } ${app.name} ${err}"
            }
        }
        log.debug "getPipelines: ${jobs.size()} pipelines in ${System.currentTimeMillis() - start}ms"
        return jobs
    }

    List<Application> getApplications() {
        new SimpleHystrixCommand<List<Application>>(groupKey, buildCommandKey("getApplications"), {
            return werckerClient.getApplications(authHeaderValue, limit)
        }).execute()
    }

    List<Pipeline> getPipelines(String org, String app) {
        new SimpleHystrixCommand<List<Pipeline>>(groupKey, buildCommandKey("getPipelines"), {
            return werckerClient.getPipelinesForApplication(authHeaderValue, org, app)
        }).execute()
    }

    Run getRunById(String runId) {
        new SimpleHystrixCommand<Run>(groupKey, buildCommandKey("getRunById"), {
            return werckerClient.getRunById(authHeaderValue, runId)
        }).execute()
    }

    Response abortRun(String runId, Map body) {
        new SimpleHystrixCommand<Response>(groupKey, buildCommandKey("abortRun"), {
            return werckerClient.abortRun(authHeaderValue, runId, body)
        }).execute()
    }

    List<Run> getRunsSince(String branch, List<String> pipelineIds, int limit, long since) {
        new SimpleHystrixCommand<List<Run>>(groupKey, buildCommandKey("getRunsSince"), {
            return werckerClient.getRunsSince(authHeaderValue, branch, pipelineIds, limit, since)
        }).execute()
    }

    List<Run> getRunsForPipeline(String pipelineId) {
        new SimpleHystrixCommand<List<Run>>(groupKey, buildCommandKey("getRunsForPipeline"), {
            return werckerClient.getRunsForPipeline(authHeaderValue, pipelineId)
        }).execute()
    }

    Pipeline getPipeline(String pipelineId) {
        new SimpleHystrixCommand<Pipeline>(groupKey, buildCommandKey("getPipeline"), {
            return werckerClient.getPipeline(authHeaderValue, pipelineId)
        }).execute()
    }

    Map<String, Object> triggerBuild(RunPayload runPayload) {
        new SimpleHystrixCommand<Map<String, Object>>(groupKey, buildCommandKey("triggerBuild"), {
            return werckerClient.triggerBuild(authHeaderValue, runPayload)
        }).execute()
    }

    /**
     * A CommandKey should be unique per group (to ensure broken circuits do not span Wercker masters)
     */
    private String buildCommandKey(String id) {
        return "${groupKey}-${id}"
    }

    List<Run> getBuilds(String appAndPipelineName) {
        String pipelineId = getPipelineId(appAndPipelineName)
        log.debug "getBuildList for ${groupKey} ${appAndPipelineName} ${pipelineId}"
        return pipelineId? getRunsForPipeline(pipelineId) : []
    }

    String pipelineKey(Run run, Map<String, String> pipelineKeys) {
        if (run.pipelineId) {
            return pipelineKeys ? pipelineKeys.get(run.pipelineId) : getPipelineName(run.pipelineId)
        } else {
            return new QualifiedPipelineName(
                run.getApplication().owner.name, run.getApplication().name, run.getPipeline().name)
                .toString()
        }
    }

    String getPipelineId(String appAndPipelineName) {
        QualifiedPipelineName qPipeline = QualifiedPipelineName.of(appAndPipelineName)
        String pipelineId = cache.getPipelineID(groupKey, appAndPipelineName)
        if (pipelineId == null) {
            try {
                List<Pipeline> pipelines = getPipelines(qPipeline.ownerName, qPipeline.appName)
                Pipeline matchingPipeline = pipelines.find {
                    pipeline -> qPipeline.pipelineName == pipeline.name
                }
                if (matchingPipeline) {
                    pipelineId = matchingPipeline.id
                    cache.setPipelineID(groupKey, appAndPipelineName, pipelineId)
                }
            } catch(retrofit.RetrofitError err) {
                log.info "Error getting pipelines for ${qPipeline.ownerName} ${qPipeline.appName} ${err} ${err.getClass()}"
            }
        }
        return pipelineId
    }

    String getPipelineName(String pipelineId) {
        String name = cache.getPipelineName(groupKey, pipelineId)
        if (name == null) {
            try {
                Pipeline pipeline = getPipeline(pipelineId)
                if (pipeline && pipeline.application && pipeline.application.owner) {
                    name = new QualifiedPipelineName(
                        pipeline.application.owner.name,
                        pipeline.application.name,
                        pipeline.name)
                        .toString()
                    cache.setPipelineID(groupKey, name, pipelineId)
                }
            } catch(retrofit.RetrofitError err) {
                log.info "Error getting pipelines for ${owner} ${appName} ${err} ${err.getClass()}"
            }
        }
        return name
    }

    Map<String, List<Run>> getRunsSince(Set<String> pipelines, long since) {
        long start = System.currentTimeMillis()
        Map<String, String> pipelineKeys = pipelines.collectEntries { [(getPipelineId(it)) : (it)] }
        List<String> pids = pipelineKeys.keySet().asList()
        List<Run> allRuns = getRunsSince(branch, pids, limit, since)
        log.debug "getRunsSince for pipelines:${pipelines} : ${allRuns.size()} runs in ${System.currentTimeMillis() - start}ms!!"
        return groupRunsByPipeline(allRuns, pipelineKeys)
    }

    Map<String, List<Run>> getRunsSince(long since) {
        long start = System.currentTimeMillis()
        List<Run> allRuns = getRunsSince(branch, [], limit, since)
        log.debug "getRunsSince ${since} : ${allRuns.size()} runs in ${System.currentTimeMillis() - start}ms!!"
        return groupRunsByPipeline(allRuns, null)
    }

    private Map<String, List<Run>> groupRunsByPipeline(List<Run> allRuns, Map<String, String> pipelineKeys) {
        Map<String, List<Run>> pipelineRuns = [:]
        allRuns.forEach({run ->
            run.startedAt = run.startedAt ?: run.createdAt
        })
        return allRuns.groupBy {run ->
            pipelineKey(run, pipelineKeys)
        }
    }

    //TODO investigate if Wercker needs the JobConfig implementation
    JobConfig getJobConfig(String jobName) {
        return new JobConfig(
                description: 'WerckerPipeline ' + jobName,
                name: jobName)
    }
}
