/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.polling

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

abstract class CommonPollingMonitor implements PollingMonitor {
    Logger log = LoggerFactory.getLogger(getClass())

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    Scheduler scheduler = Schedulers.io()

    Scheduler.Worker worker = scheduler.createWorker()

    Long lastPoll

    @Override
    void onApplicationEvent(RemoteStatusChangedEvent event) {
        log.info('Started')
        initialize()
        worker.schedulePeriodically({
            if (isInService()) {
                lastPoll = System.currentTimeMillis()
                poll()
            } else {
                log.info("not in service (lastPoll: ${lastPoll ?: 'n/a'})")
                lastPoll = null
            }
        } as Action0, 0, pollInterval, TimeUnit.SECONDS)
    }

    abstract void initialize()
    abstract void poll()

    @Override
    boolean isInService() {
        if (discoveryClient == null) {
            log.info("no DiscoveryClient, assuming InService")
            true
        } else {
            def remoteStatus = discoveryClient.instanceRemoteStatus
            log.info("current remote status ${remoteStatus}")
            remoteStatus == InstanceInfo.InstanceStatus.UP
        }
    }

    @Override
    int getPollInterval() {
        return igorConfigurationProperties.spinnaker.build.pollInterval
    }

    @Override
    Long getLastPoll() {
        return lastPoll
    }
}
