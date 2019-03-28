/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.WerckerProperties
import com.netflix.spinnaker.igor.config.WerckerProperties.WerckerHost
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.wercker.model.Application
import com.netflix.spinnaker.igor.wercker.model.Owner
import com.netflix.spinnaker.igor.wercker.model.Pipeline
import com.netflix.spinnaker.igor.wercker.model.Run
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent

import java.util.concurrent.TimeUnit

import rx.schedulers.TestScheduler
import spock.lang.Specification

class WerckerBuildMonitorSpec extends Specification {

    WerckerCache cache = Mock(WerckerCache)
    WerckerBuildMonitor monitor
    EchoService echoService = Mock(EchoService)
    WerckerClient client
    WerckerService mockService = Mock(WerckerService)
    WerckerService werckerService
    String werckerDev = 'https://dev.wercker.com/'
    String master = 'WerckerTestMaster'

    void setup() {
        client = Mock(WerckerClient)
        werckerService = new WerckerService(
                new WerckerHost(name: master, address: werckerDev), cache, client, Permissions.EMPTY)
    }

    final MASTER = 'MASTER'
    final pipeline = 'myOrg/myApp/pollingTest'
    final TestScheduler scheduler = new TestScheduler()

    BuildServices mockBuildServices() {
        cache.getJobNames(MASTER) >> ['pipeline']
        BuildServices buildServices = new BuildServices()
        buildServices.addServices([MASTER: mockService])
        return buildServices
    }

    void 'no finished run'() {
        given:
        BuildServices buildServices = mockBuildServices()

        long now = System.currentTimeMillis();
        List<Run> runs1 = [
            new Run(id:"b",    startedAt: new Date(now-10)),
            new Run(id:"a",    startedAt: new Date(now-11)),
            new Run(id:"init", startedAt: new Date(now-12)),
        ]
        monitor = monitor(buildServices)
        monitor.worker = scheduler.createWorker()
        mockService.getRunsSince(_) >> [pipeline: runs1]
        cache.getBuildNumber(*_) >> 1
        mockService.getBuildServiceProvider() >> BuildServiceProvider.WERCKER

        when:
        monitor.onApplicationEvent(Mock(RemoteStatusChangedEvent))
        scheduler.advanceTimeBy(1L, TimeUnit.SECONDS.MILLISECONDS)

        then: 'initial poll'
        0 * echoService.postEvent(_)

        cleanup:
        monitor.stop()
    }

    void 'initial poll with completed runs'() {
        given:
        BuildServices buildServices = mockBuildServices()

        long now = System.currentTimeMillis();
        List<Run> runs1 = [
            new Run(id:"b",    startedAt: new Date(now-10)),
            new Run(id:"a",    startedAt: new Date(now-11), finishedAt: new Date(now-11)),
            new Run(id:"init", startedAt: new Date(now-12), finishedAt: new Date(now-10)),
        ]
        monitor = monitor(buildServices)
        monitor.worker = scheduler.createWorker()
        mockService.getRunsSince(_) >> [pipeline: runs1]
        cache.getBuildNumber(*_) >> 1
        mockService.getBuildServiceProvider() >> BuildServiceProvider.WERCKER

        when:
        monitor.onApplicationEvent(Mock(RemoteStatusChangedEvent))
        scheduler.advanceTimeBy(1L, TimeUnit.SECONDS.MILLISECONDS)

        then: 'initial poll'
        1 * echoService.postEvent(_)

        cleanup:
        monitor.stop()
    }

    void 'select latest one from multiple completed runs'() {
        given:
        BuildServices buildServices = mockBuildServices()
        monitor = monitor(buildServices)
        monitor.worker = scheduler.createWorker()
        mockService.getRunsSince(_) >> [:]
        cache.getBuildNumber(*_) >> 1
        mockService.getBuildServiceProvider() >> BuildServiceProvider.WERCKER

        when:
        monitor.onApplicationEvent(Mock(RemoteStatusChangedEvent))
        scheduler.advanceTimeBy(1L, TimeUnit.SECONDS.MILLISECONDS)

        then: 'initial poll'
        0 * echoService.postEvent(_)

        when:
        scheduler.advanceTimeBy(998L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        0 * mockService.getRunsSince(_) >> [:]

        when: 'poll at 1 second'
        long now = System.currentTimeMillis();
        List<Run> runs1 = [
            new Run(id:"b",    startedAt: new Date(now-10)),
            new Run(id:"a",    startedAt: new Date(now-11), finishedAt: new Date(now-9)),
            new Run(id:"init", startedAt: new Date(now-12), finishedAt: new Date(now-8)),
        ]
        cache.getLastPollCycleTimestamp(_, _) >> (now - 1000)
        mockService.getRunsSince(_) >> [pipeline: runs1]
        cache.getBuildNumber(*_) >> 1
        scheduler.advanceTimeBy(2L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        1 * cache.setEventPosted('MASTER', 'pipeline', 'init')
        1 * echoService.postEvent(_)

        cleanup:
        monitor.stop()
    }

    void 'get runs of multiple pipelines'() {
        setup:
        BuildServices buildServices = new BuildServices()
        buildServices.addServices([MASTER: werckerService])
        monitor = monitor(buildServices)
        monitor.worker = scheduler.createWorker()
        cache.getBuildNumber(*_) >> 1
        client.getRunsSince(_, _, _, _, _) >> []
        mockService.getBuildServiceProvider() >> BuildServiceProvider.WERCKER

        when:
        monitor.onApplicationEvent(Mock(RemoteStatusChangedEvent))
        scheduler.advanceTimeBy(1L, TimeUnit.SECONDS.MILLISECONDS)

        then: 'initial poll'
        0 * echoService.postEvent(_)

        when:
        scheduler.advanceTimeBy(998L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        0 * echoService.postEvent(_)

        when: 'poll at 1 second'
        long now = System.currentTimeMillis();
        def org = 'myOrg'
        def apps = [
            appOf('app0', org, [pipeOf('p00', 'pipeline')]),
            appOf('app1', org, [
                pipeOf('p10', 'git'),
                pipeOf('p11', 'git')
            ]),
            appOf('app2', org, [pipeOf('p20', 'git')]),
            appOf('app3', org, [pipeOf('p30', 'git')])
        ]
        List<Run> runs1 = [
            runOf('run0', now-10, now-1, apps[0], apps[0].pipelines[0]),
            runOf('run1', now-10, now-1, apps[1], apps[1].pipelines[0]),
            runOf('run2', now-10, now-2, apps[2], apps[2].pipelines[0]),
            runOf('run3', now-10, now-1, apps[1], apps[1].pipelines[1]),
            runOf('run4', now-10, now-1, apps[2], apps[2].pipelines[0]),
            runOf('run5', now-10, null,  apps[3], apps[3].pipelines[0]),
            runOf('run6', now-10, now-2, apps[0], apps[0].pipelines[0]),
            runOf('run6', now-10, now-3, apps[0], apps[0].pipelines[0]),
        ]
        client.getRunsSince(_,_,_,_,_) >> runs1
        cache.getLastPollCycleTimestamp(_, _) >> (now - 1000)
        cache.getBuildNumber(*_) >> 1
        scheduler.advanceTimeBy(2L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        1 * cache.setEventPosted('MASTER', 'myOrg/app0/p00', 'run0')
        1 * cache.setEventPosted('MASTER', 'myOrg/app1/p10', 'run1')
        1 * cache.setEventPosted('MASTER', 'myOrg/app1/p11', 'run3')
        1 * cache.setEventPosted('MASTER', 'myOrg/app2/p20', 'run4')
        0 * cache.setEventPosted('MASTER', 'myOrg/app3/p30', 'run5')
        4 * echoService.postEvent(_)

        cleanup:
        monitor.stop()
    }

    WerckerBuildMonitor monitor(BuildServices buildServices) {
        def cfg = new IgorConfigurationProperties()
        cfg.spinnaker.build.pollInterval = 1
        return new WerckerBuildMonitor(
                cfg,
                new NoopRegistry(),
                Optional.empty(),
                Optional.empty(),
                cache,
                buildServices,
                true,
                Optional.of(echoService),
                new WerckerProperties()
                )
    }

    Application appOf(String name, String owner, List<Pipeline> pipelines) {
        return new Application(name: name, owner: new Owner(name: owner), pipelines: pipelines)
    }

    Pipeline pipeOf(String name, String type, String id=name) {
        return new Pipeline(id: id, name: name, type: type)
    }

    Run runOf(String id, long startedAt, Long finishedAt, Application app, Pipeline pipe) {
        return new Run(id: id, startedAt: new Date(startedAt), finishedAt: finishedAt? new Date(finishedAt) : null, application: app, pipeline: pipe)
    }
}
