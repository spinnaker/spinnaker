/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker

import static com.netflix.spinnaker.igor.wercker.model.Run.finishedAtComparator
import static com.netflix.spinnaker.igor.wercker.model.Run.startedAtComparator
import static net.logstash.logback.argument.StructuredArguments.kv

import com.netflix.discovery.DiscoveryClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericProject
import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.config.WerckerProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.GenericBuildContent
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.DeltaItem
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.polling.PollingDelta
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.wercker.model.Run

import groovy.time.TimeCategory

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

import java.util.stream.Collectors

import javax.annotation.PreDestroy

import retrofit.RetrofitError

/**
 * Monitors new wercker runs
 */
@Service
@ConditionalOnProperty('wercker.enabled')
class WerckerBuildMonitor extends CommonPollingMonitor<PipelineDelta, PipelinePollingDelta> {

    private final WerckerCache cache
    private final BuildServices buildServices
    private final boolean pollingEnabled
    private final Optional<EchoService> echoService
    private final WerckerProperties werckerProperties

    @Autowired
    WerckerBuildMonitor(
        IgorConfigurationProperties properties,
        Registry registry,
        Optional<DiscoveryClient> discoveryClient,
        Optional<LockService> lockService,
        WerckerCache cache,
        BuildServices buildServices,
        @Value('${wercker.polling.enabled:true}') boolean pollingEnabled,
        Optional<EchoService> echoService,
        WerckerProperties werckerProperties) {
        super(properties, registry, discoveryClient, lockService)
        this.cache = cache
        this.buildServices = buildServices
        this.pollingEnabled = pollingEnabled
        this.echoService = echoService
        this.werckerProperties = werckerProperties
    }

    @Override
    String getName() {
        "WerckerBuildMonitor"
    }

    @Override
    boolean isInService() {
        pollingEnabled && super.isInService()
    }

    @Override
    void initialize() {
    }

    @Override
    void poll(boolean sendEvents) {
        long startTime = System.currentTimeMillis()
        log.info "WerckerBuildMonitor Polling cycle started: ${new Date()}, echoService:${echoService.isPresent()} "
        buildServices.getServiceNames(BuildServiceProvider.WERCKER).parallelStream().forEach( { master ->
            pollSingle(new PollContext(master, !sendEvents))
        }
        )
        log.info "WerckerBuildMonitor Polling cycle done in ${System.currentTimeMillis() - startTime}ms"
    }

    @PreDestroy
    void stop() {
        log.info('Stopped')
        if (!worker.isUnsubscribed()) {
            worker.unsubscribe()
        }
    }

    /**
     * Gets a list of pipelines for this master & processes runs between last poll stamp and a sliding upper bound stamp,
     * the cursor will be used to advanced to the upper bound when all builds are completed in the commit phase.
     */
    @Override
    protected PipelinePollingDelta generateDelta(PollContext ctx) {
        String master = ctx.partitionName
        log.info("Checking for new builds for $master")
        def startTime = System.currentTimeMillis()

        List<PipelineDelta> delta = []

        WerckerService werckerService = buildServices.getService(master) as WerckerService
        long since = System.currentTimeMillis() - (Long.valueOf(getPollInterval() * 2 * 1000))
        try {
            Map<String, List<Run>> runs = werckerService.getRunsSince(since)
            runs.keySet().forEach( { pipeline ->
                processRuns(werckerService, master, pipeline, delta, runs.get(pipeline))
            } )
        } catch (e) {
            log.error("Error processing runs for Wercker[{}]", kv("master", master), e)
        }
        log.debug("Took ${System.currentTimeMillis() - startTime}ms to retrieve Wercker pipelines (master: {})", kv("master", master))
        return new PipelinePollingDelta(master: master, items: delta)
    }

    Run getLastFinishedAt(List<Run> runs) {
        return (runs && runs.size() > 0) ? Collections.max(runs, finishedAtComparator) : null
    }

    Run getLastStartedAt(List<Run> runs) {
        return Collections.max(runs, startedAtComparator)
    }

    /**
     * wercker.pipeline = project|job
     * wercker.run = build
     */
    private void processRuns( WerckerService werckerService, String master, String pipeline,
            List<PipelineDelta> delta, List<Run> runs) {
        List<Run> allRuns = runs ?: werckerService.getBuilds(pipeline)
        log.info "polling Wercker pipeline: ${pipeline} got ${allRuns.size()} runs"
        if (allRuns.empty) {
            log.debug("[{}:{}] has no runs skipping...", kv("master", master), kv("pipeline", pipeline))
            return
        }
        Run lastStartedAt = getLastStartedAt(allRuns)
        try {
            Long cursor = cache.getLastPollCycleTimestamp(master, pipeline)
            //The last build/run
            Long lastBuildStamp = lastStartedAt.startedAt.fastTime
            Date upperBound     = lastStartedAt.startedAt
            if (cursor == lastBuildStamp) {
                log.debug("[${master}:${pipeline}] is up to date. skipping")
                return
            }
            cache.updateBuildNumbers(master, pipeline, allRuns)
            List<Run> allBuilds = allRuns.findAll { it?.startedAt?.fastTime > cursor }
            if (!cursor && !igorProperties.spinnaker.build.handleFirstBuilds) {
                cache.setLastPollCycleTimestamp(master, pipeline, lastBuildStamp)
                return
            }
            List<Run> currentlyBuilding = allBuilds.findAll { it.finishedAt == null }
            //If there are multiple completed runs, use only the latest finished one
            log.debug "allNewBuilds: ${allBuilds}"
            Run lastFinished = getLastFinishedAt(allBuilds)
            List<Run> completedBuilds = (lastFinished && lastFinished.finishedAt)? [lastFinished]: []
            log.debug("[${master}:${pipeline}] currentlyBuilding: ${currentlyBuilding}" )
            log.debug("[${master}:${pipeline}]   completedBuilds: ${completedBuilds}" )
            cursor = cursor?:lastBuildStamp
            Date lowerBound = new Date(cursor)
            if (!igorProperties.spinnaker.build.processBuildsOlderThanLookBackWindow) {
                completedBuilds = onlyInLookBackWindow(completedBuilds)
            }
            delta.add(new PipelineDelta(
                    cursor: cursor,
                    name: pipeline,
                    lastBuildStamp: lastBuildStamp,
                    upperBound: upperBound,
                    lowerBound: lowerBound,
                    completedBuilds: completedBuilds,
                    runningBuilds: currentlyBuilding
                    ))
        } catch (e) {
            log.error("Error processing runs for [{}:{}]", kv("master", master), kv("pipeline", pipeline), e)
            if (e.cause instanceof RetrofitError) {
                def re = (RetrofitError) e.cause
                log.error("Error communicating with Wercker for [{}:{}]: {}", kv("master", master), kv("pipeline", pipeline), kv("url", re.url), re)
            }
        }
    }

    private List<Run> onlyInLookBackWindow(List<Run> builds) {
        use(TimeCategory) {
            def offsetSeconds = pollInterval.seconds
            def lookBackWindowMins = igorProperties.spinnaker.build.lookBackWindowMins.minutes
            Date lookBackDate = (offsetSeconds + lookBackWindowMins).ago
            return builds.stream().filter({
                Date buildEndDate = it.finishedAt
                return buildEndDate.after(lookBackDate)
            }).collect(Collectors.toList())
        }
    }

    private GenericBuild toBuild(String master, String pipeline, Run run) {
        Result res = (run.finishedAt == null) ? Result.BUILDING : (run.result.equals("passed")? Result.SUCCESS : Result.FAILURE)
        return new GenericBuild (
                building: (run.finishedAt == null),
                result: res,
                number: cache.getBuildNumber(master, pipeline, run.id),
                timestamp: run.startedAt.fastTime as String,
                id: run.id,
                url: run.url
                )
    }

    @Override
    protected void commitDelta(PipelinePollingDelta delta, boolean sendEvents) {
        String master = delta.master
        delta.items.parallelStream().forEach { pipeline ->
            //job = Wercker pipeline (org/app/pipeline)
            // post event for latest finished run
            pipeline.completedBuilds.forEach { run ->
                //build = Wercker run
                Boolean eventPosted = cache.getEventPosted(master, pipeline.name, run.id)
                GenericBuild build = toBuild(master, pipeline.name, run)
                if (!eventPosted && sendEvents) {
                    log.debug("[${master}:${pipeline.name}]:${build.id} event posted")
                    if(postEvent(new GenericProject(pipeline.name, build), master)) {
                        cache.setEventPosted(master, pipeline.name, run.id)
                    }
                }
            }

            // advance cursor when all builds have completed in the interval
            if (pipeline.runningBuilds.isEmpty()) {
                log.info("[{}:{}] has no other builds between [${pipeline.lowerBound} - ${pipeline.upperBound}], advancing cursor to ${pipeline.lastBuildStamp}", kv("master", master), kv("pipeline", pipeline.name))
                cache.pruneOldMarkers(master, pipeline.name, pipeline.cursor)
                cache.setLastPollCycleTimestamp(master, pipeline.name, pipeline.lastBuildStamp)
            }
        }
    }

    @Override
    protected Integer getPartitionUpperThreshold(String partition) {
        return werckerProperties.masters.find { partition == it.name }?.itemUpperThreshold
    }

    private boolean postEvent(GenericProject project, String master) {
        if (!echoService.isPresent()) {
            log.warn("Cannot send build notification: Echo is not configured")
            registry.counter(missedNotificationId.withTag("monitor", getClass().simpleName)).increment()
            return false;
        }
        echoService.get().postEvent(new GenericBuildEvent(content: new GenericBuildContent(project: project, master: master, type: "wercker")))
        return true;
    }

    private static class PipelinePollingDelta implements PollingDelta<PipelineDelta> {
        String master
        List<PipelineDelta> items
    }

    private static class PipelineDelta implements DeltaItem {
        Long cursor
        String name
        Long lastBuildStamp
        Date lowerBound
        Date upperBound
        List<Run> completedBuilds
        List<Run> runningBuilds
    }
}
