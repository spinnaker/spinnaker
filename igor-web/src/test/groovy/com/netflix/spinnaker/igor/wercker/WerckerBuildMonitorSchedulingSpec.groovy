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
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.WerckerProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent

import java.util.concurrent.TimeUnit

import rx.schedulers.TestScheduler
import spock.lang.Specification

/**
 * Ensures that build monitor runs periodically
 */
class WerckerBuildMonitorSchedulingSpec extends Specification {

    WerckerCache cache = Mock(WerckerCache)
    WerckerService werckerService = Mock(WerckerService)
    WerckerBuildMonitor monitor

    final MASTER = 'MASTER'
    final TestScheduler scheduler = new TestScheduler()

    void 'scheduler polls periodically'() {
        given:
        cache.getJobNames(MASTER) >> []
        BuildMasters buildMasters = Mock(BuildMasters)
        buildMasters.map >> [MASTER: werckerService]
        werckerService.getRunsSince(_) >> [:]
        def cfg = new IgorConfigurationProperties()
        cfg.spinnaker.build.pollInterval = 1
        monitor = new WerckerBuildMonitor(
                cfg,
                new NoopRegistry(),
                Optional.empty(),
                Optional.empty(),
                cache,
                buildMasters,
                true,
                Optional.empty(),
                new WerckerProperties()
                )
        monitor.worker = scheduler.createWorker()
        when:
        monitor.onApplicationEvent(Mock(RemoteStatusChangedEvent))
        scheduler.advanceTimeBy(1L, TimeUnit.SECONDS.MILLISECONDS)

        then: 'initial poll'
        1 * buildMasters.filteredMap(BuildServiceProvider.WERCKER) >> [MASTER: werckerService]
        1 * buildMasters.map >> [MASTER: werckerService]
        1 * werckerService.getRunsSince(_) >> [:]

        when:
        scheduler.advanceTimeBy(998L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        0 * buildMasters.map >> [MASTER: werckerService]
        0 * werckerService.getRunsSince(_) >> [:]

        when: 'poll at 1 second'
        scheduler.advanceTimeBy(2L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        1 * buildMasters.filteredMap(BuildServiceProvider.WERCKER) >> [MASTER: werckerService]
        1 * buildMasters.map >> [MASTER: werckerService]
        1 * werckerService.getRunsSince(_) >> [:]

        when: 'poll at 5 second'
        scheduler.advanceTimeBy(4000L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        4 * buildMasters.filteredMap(BuildServiceProvider.WERCKER) >> [MASTER: werckerService]
        4 * buildMasters.map >> [MASTER: werckerService]
        4 * werckerService.getRunsSince(_) >> [:]

        cleanup:
        monitor.stop()
    }

    void 'scheduler can be turned off'() {
        given:
        cache.getJobNames(MASTER) >> []
        BuildMasters buildMasters = Mock(BuildMasters)
        def cfg = new IgorConfigurationProperties()
        cfg.spinnaker.build.pollInterval = 1
        monitor = new WerckerBuildMonitor(
                cfg,
                new NoopRegistry(),
                Optional.empty(),
                Optional.empty(),
                cache,
                buildMasters,
                false,
                Optional.empty(),
                new WerckerProperties()
                )
        monitor.worker = scheduler.createWorker()

        when:
        monitor.onApplicationEvent(Mock(RemoteStatusChangedEvent))
        scheduler.advanceTimeBy(1L, TimeUnit.SECONDS.MILLISECONDS)

        then: 'initial poll'
        0 * buildMasters.filteredMap

        when:
        scheduler.advanceTimeBy(998L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        0 * buildMasters.filteredMap

        when: 'no poll at 1 second'
        scheduler.advanceTimeBy(2L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        0 * buildMasters.filteredMap

        cleanup:
        monitor.stop()
    }
}
