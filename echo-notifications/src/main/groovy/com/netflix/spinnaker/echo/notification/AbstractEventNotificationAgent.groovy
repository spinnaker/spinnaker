package com.netflix.spinnaker.echo.notification

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.echo.echo.EchoService
import com.netflix.spinnaker.echo.mayo.MayoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import rx.Observable
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

abstract class AbstractEventNotificationAgent {

    @Autowired
    EchoService echoService

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    @Autowired
    MayoService mayoService

    @Value('${failure.pollInterval:15}')
    int pollInterval

    long lastCheck = System.currentTimeMillis()

    @Value('${spinnaker.baseUrl}')
    String spinnakerUrl

    Scheduler.Worker worker = Schedulers.io().createWorker()

    static List<Map<String, String>> CONFIG = [
            [
                    type: 'pipeline',
                    link: 'executions'
            ],
            [
                    type: 'task',
                    link: 'tasks'
            ]
    ]

    static List STATUSES = [
            'starting', 'complete', 'failed'
    ]

    @PreDestroy
    void stop() {
        log.info('Stopped')
        if (!worker.isUnsubscribed()) {
            worker.unsubscribe()
        }
    }

    @PostConstruct
    void start() {
        log.info('Starting to monitor workers')
        lastCheck = System.currentTimeMillis()
        worker.schedulePeriodically(
                {
                    check()
                } as Action0, 0, pollInterval, TimeUnit.SECONDS
        )
    }

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


    void check() {
        try {
            if (isInService()) {
                log.info("checking for new events $lastCheck (${new Date(lastCheck)})")

                Observable.from([CONFIG, STATUSES].combinations()).subscribe(
                        { List combination ->
                            Map<String, String> config = combination.first()
                            String status = combination.last()

                            Observable.from(
                                    echoService.getEvents("orca:${config.type}:${status}", lastCheck)
                            ).subscribe(
                                    { Map event ->

                                        if (config.type == 'task' && event.content.standalone == false) {
                                            return
                                        }

                                        if (config.type == 'task' && event.content.canceled == true) {
                                            return
                                        }

                                        if (config.type == 'pipeline' && event.content.execution?.canceled == true) {
                                            return
                                        }

                                        sendNotifications(event, config, status)

                                    }, {
                                log.error("Error: ${it.message}")
                            }, {} as Action0
                            )
                        }, {
                    log.error("Error: ${it.message}")
                }, {} as Action0
                )
            } else {
                log.info("Not in service : " + lastCheck)
            }
            lastCheck = System.currentTimeMillis()
        }
        catch (e) {
            log.error('error in email agent', e)
        }
    }

    abstract void sendNotifications(Map event, Map config, String status)

}
