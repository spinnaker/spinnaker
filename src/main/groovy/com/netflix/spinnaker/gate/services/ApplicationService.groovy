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
import retrofit.RetrofitError
import rx.Observable
import rx.schedulers.Schedulers

@CompileStatic
@Component
class ApplicationService implements CacheEnabledService {
  private static final String SERVICE = "applications"
  private static final HystrixCommandGroupKey HYSTRIX_KEY = HystrixCommandGroupKey.Factory.asKey(SERVICE)

  @Autowired
  OortService oortService

  @Autowired
  OrcaService orcaService

  @Autowired
  Front50Service front50Service

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
    Observable.just(getApp(name), getMetaData("test", name), getMetaData("prod", name)).observeOn(Schedulers.io())
        .flatMap {
      it
    }.map {
      it
    }.reduce([:], { Map map, Map input ->
      if (input.containsKey("attributes")) {
        map.putAll(input)
      } else {
        ((Map) map.attributes).putAll(input)
      }
      map
    })
  }

  private Observable<Map> getApp(String name) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("get"))) {

      @Override
      protected Observable<Map> run() {
        Observable.just(oortService.getApplication(name))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([:])
      }

      @Override
      protected String getCacheKey() {
        "application-${name}"
      }
    }.toObservable()
  }

  private Observable<Map> getMetaData(String account, String name) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getMetaData"))) {

      @Override
      protected Observable<Map> run() {
        try {
          Observable.just(front50Service.getMetaData(account, name))
        } catch (RetrofitError e) {
          if (e.response.status == 404) {
            getFallback()
          } else {
            throw e
          }
        }
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([:])
      }

      @Override
      protected String getCacheKey() {
        "getMetaData-${account}-${name}"
      }
    }.toObservable()
  }

  // TODO Hystrix fallback?
  Observable<List> getTasks(String app) {
    Preconditions.checkNotNull(app)
    orcaService.getTasks(app)
  }
  // TODO Hystrix fallback?
  Observable<List> getPipelines(String app) {
    Preconditions.checkNotNull(app)
    orcaService.getPipelines(app)
  }

}
