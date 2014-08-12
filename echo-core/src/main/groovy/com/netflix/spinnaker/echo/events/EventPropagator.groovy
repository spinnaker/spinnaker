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
import rx.Observable
import rx.functions.Action0

/**
 *  responsible for sending events to classes that implement an EchoEventListener
 */
@Slf4j
class EventPropagator {

    List<EchoEventListener> listeners = []

    void addListener(EchoEventListener listener) {
        listeners << listener
        log.info('Added listener ' + listener.class.simpleName)
    }

    void processEvent(Event event) {
        Observable.from(listeners).subscribe(
            { EchoEventListener listener ->
                listener.processEvent(event)
            }, {
            log.error("Error: ${it.message}")
        }, {
        } as Action0
        )
    }

}
