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
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import rx.Observable
import rx.functions.Func1
import rx.functions.Func2
import rx.functions.FuncN
import rx.schedulers.Schedulers

@Component
class ApplicationService {
  private static final String SERVICE = "applications"
  private static final HystrixCommandGroupKey HYSTRIX_KEY = HystrixCommandGroupKey.Factory.asKey(SERVICE)

  @Autowired
  OortService oortService

  @Autowired
  OrcaService orcaService

  @Autowired
  Front50Service front50Service

  @Autowired
  CredentialsService credentialsService

  static HystrixCommandProperties.Setter createHystrixCommandPropertiesSetter(){
    HystrixCommandProperties.invokeMethod("Setter", null)
  }

  Observable<List<Map>> getAll() {
    new HystrixObservableCommand<List<Map>>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getAll"))
        .andCommandPropertiesDefaults(createHystrixCommandPropertiesSetter()
                                        .withExecutionIsolationThreadTimeoutInMilliseconds(30000))) {
      @Override
      protected Observable<List<Map>> run() {
        credentialsService.getAccountNames().flatMap { String name ->
          rx.Observable.from(front50Service.getAll(name))
        }.toList()
      }

      @Override
      protected Observable<List<Map>> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "applications-all"
      }
    }.observe()
  }

  Observable<Map> get(String name) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getApp-${name}"))
        .andCommandPropertiesDefaults(createHystrixCommandPropertiesSetter()
        .withExecutionIsolationThreadTimeoutInMilliseconds(30000))) {
      @Override
      protected Observable<Map> run() {
        Observable.just(oortService.getApplication(name)).mergeWith(credentialsService.accountNames.flatMap { String account ->
          rx.Observable.just(front50Service.getMetaData(account, name))
        }).onErrorReturn(new Func1<Throwable, Map>() {
          @Override
          Map call(Throwable throwable) {
            [:]
          }
        }).observeOn(Schedulers.io()).map {
          it
        }.reduce([:], { Map app, Map data ->
          if (data.containsKey("clusters")) {
            app.putAll data
          } else {
            if (!app.containsKey("attributes")) {
              app.attributes = [:]
            }
            app.attributes.putAll(data)
          }
          app
        })
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "applications-${name}"
      }
    }.observe()

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

  Observable<Map> create(Map<String, String> app) {
    def account = app.remove("account")
    orcaService.doOperation([application: app.name, description: "Create application (Gate",
                             job: [type: "createApplication", application: app, account: account]])
  }

}
