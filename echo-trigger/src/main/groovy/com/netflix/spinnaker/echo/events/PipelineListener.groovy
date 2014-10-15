/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.echo.events

import com.netflix.spinnaker.echo.model.Event
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

/**
 * Event listener for triggering events
 */
@Component
@Slf4j
class PipelineListener implements EchoEventListener, ApplicationListener<ContextRefreshedEvent> {

    @Autowired(required = false)
    MayoService mayo

    @Autowired(required = false)
    OrcaService orca

    Scheduler.Worker worker

    Map jobsList = [:]

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        if (mayo && orca) {
            jobsList = mayo.allJobs
            worker = Schedulers.io().createWorker()
            worker.schedulePeriodically(
                {
                    jobsList = mayo.allJobs
                    log.info('Refreshing list of jobs')
                } as Action0, 0, 10, TimeUnit.SECONDS
            )
        }
    }

    @Override
    void processEvent(Event event) {
        if (mayo && orca) {
            if (event.details.source == 'igor') {
                String master = event.content.master
                String jobName = event.content.jobName
                if (jobsList[master]?.contains(jobName)) {
                    mayo.getPipelines(master, jobName).each { pipeline ->
                        orca.triggerBuild(pipeline)
                        log.info('triggering pipeline {} for {}/{}', pipeline.name, master, jobName)
                    }
                }
            }
        }
    }

}
