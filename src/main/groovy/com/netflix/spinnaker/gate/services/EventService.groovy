/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.hystrix.HystrixCommandGroupKey
import com.netflix.hystrix.HystrixCommandKey
import com.netflix.hystrix.HystrixObservableCommand
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Observable
import rx.functions.Action1

@Component
class EventService {
  private static final String SERVICE = "events"
  private static final HystrixCommandGroupKey HYSTRIX_KEY = HystrixCommandGroupKey.Factory.asKey(SERVICE)

  @Autowired
  EchoService echoService

  Observable<Map> getAll(int offset, int size) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getForApp"))) {

      @Override
      protected Observable<Map> run() {
        Observable.just(echoService.getAllEvents(offset, size, true))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.just([:])
      }

      @Override
      protected String getCacheKey() {
        "events-all"
      }
    }.toObservable()
  }

  Observable<Map> getForApplication(String app) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getForApp"))) {

      @Override
      protected Observable<Map> run() {
        Observable.just(echoService.getEvents(app, 0, Integer.MAX_VALUE, true))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([:])
      }

      @Override
      protected String getCacheKey() {
        "events-${app}"
      }
    }.toObservable()
  }
}
