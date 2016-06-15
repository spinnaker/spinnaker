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

package com.netflix.spinnaker.igor.docker

import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts
import com.netflix.spinnaker.igor.docker.service.TaggedImage
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.DockerEvent
import com.netflix.spinnaker.igor.polling.PollingMonitor
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Service
import rx.Observable
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

@Slf4j
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('dockerRegistry.enabled')
class DockerMonitor implements PollingMonitor {

    Scheduler.Worker worker = Schedulers.io().createWorker()

    @Autowired
    DockerRegistryCache cache

    @Autowired
    DockerRegistryAccounts dockerRegistryAccounts

    @Autowired(required = false)
    EchoService echoService

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')
        worker.schedulePeriodically(
            {
                if (isInService()) {
                    dockerRegistryAccounts.updateAccounts()
                    dockerRegistryAccounts.accounts.forEach { account ->
                        changedTags(account)
                    }
                } else {
                    log.info("not in service (lastPoll: ${lastPoll ?: 'n/a'})")
                    lastPoll = null
                }
            } as Action0, 0, pollInterval, TimeUnit.SECONDS
        )
    }

    private void changedTags(String account) {
        log.info 'Checking for new digests for ' + account

        try {
            lastPoll = System.currentTimeMillis()
            List<String> cachedImages = cache.getImages(account)

            def startTime = System.currentTimeMillis()
            List<TaggedImage> images = dockerRegistryAccounts.service.getImagesByAccount(account)
            log.info("Took ${System.currentTimeMillis() - startTime}ms to retrieve images (account: ${account})")

            Map<String, TaggedImage> imageIds = images.collectEntries {
                [(cache.makeKey(account, it.registry, it.repository, it.tag)): it]
            }
            Observable.from(cachedImages).filter { String id ->
                !(id in imageIds)
            }.subscribe( { String imageId ->
                log.info "Removing $imageId."
                cache.remove(imageId)
            }, {
                log.error("Error: ${it.message}")
            }, {} as Action0
            )

            Observable.from(images).subscribe( { TaggedImage image ->
                def imageId = cache.makeKey(account, image.registry, image.repository, image.tag)
                def updateCache = false

                if (imageId in cachedImages) {
                    def lastDigest = cache.getLastDigest(image.account, image.registry, image.repository, image.tag)

                    if (lastDigest != image.digest) {
                        log.info "Updated tagged image: ${image.account}: ${imageId}. Digest changed from [$lastDigest] -> [$image.digest]."
                        // If either is null, there was an error retrieving the manifest in this or the previous cache cycle.
                        updateCache = image.digest != null && lastDigest != null
                    }
                } else {
                    log.info "New tagged image: ${image.account}: ${imageId}. Digest is now [$image.digest]."
                    updateCache = true
                }

                if (updateCache) {
                    if (echoService) {
                        log.info "Sending tagged image info to echo: ${image.account}: ${imageId}"
                        echoService.postEvent(new DockerEvent(content: new DockerEvent.Content(
                                registry: image.registry,
                                repository: image.repository,
                                tag: image.tag,
                                digest: image.digest,
                                account: image.account,
                        )))
                    }

                    cache.setLastDigest(image.account, image.registry, image.repository, image.tag, image.digest)
                }
            }, {
                log.error("Error: ${it.message} (${account})")
            }, {
            } as Action0

            )
        } catch (Exception e) {
            log.error "Failed to update account $account", e
        }
    }

    @Override
    String getName() {
        "dockerTagMonitor"
    }

    @Override
    boolean isInService() {
        dockerRegistryAccounts.service != null
    }

    Long lastPoll

    @Override
    Long getLastPoll() {
        lastPoll
    }

    @SuppressWarnings('GStringExpressionWithinString')
    @Value('${spinnaker.build.pollInterval:60}')
    int pollInterval

    @Override
    int getPollInterval() {
        pollInterval
    }
}
