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

import com.google.common.base.Preconditions
import com.netflix.hystrix.*
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import rx.Observable

@CompileStatic
@Component
class ApplicationService implements CacheEnabledService {
  private static final String SERVICE = "applications"
  private static final HystrixCommandGroupKey HYSTRIX_KEY = HystrixCommandGroupKey.Factory.asKey(SERVICE)

  @Autowired
  OortService oortService

  @Autowired
  PondService pondService

  @Autowired
  CacheInvalidationService cacheInvalidationService

  @CacheEvict(value = "applications", allEntries = true)
  void evict() {
  }

  @Cacheable("applications")
  Observable<Map> getAll() {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getAll"))) {

      @Override
      protected Observable<Map> run() {
        Observable.from(oortService.applications)
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "applications-all"
      }
    }.toObservable()
  }

  @Cacheable("application")
  Observable<Map> get(String name) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("get"))) {

      @Override
      protected Observable<Map> run() {
        Observable.just(oortService.getApplication(name))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "application"
      }
    }.toObservable()
  }

  Observable<Map> create(Map body) {
    pondService.doOperation(body).map({
      cacheInvalidationService.invalidateAll()
      it
    })
  }

  // TODO Hystrix fallback?
  Observable<List> getTasks(String app) {
    Preconditions.checkNotNull(app)
    pondService.getTasks(app)
  }

}
