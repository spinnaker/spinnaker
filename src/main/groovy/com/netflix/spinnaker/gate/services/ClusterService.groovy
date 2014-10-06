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

import com.netflix.hystrix.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Observable

@Component
class ClusterService {
  private static final String SERVICE = "clusters"
  private static final HystrixCommandGroupKey HYSTRIX_KEY = HystrixCommandGroupKey.Factory.asKey(SERVICE)

  @Autowired
  OortService oortService

  Observable<Map> getClusters(String app, String account) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getClusters"))) {

      @Override
      protected Observable<Map> run() {
        Observable.from(oortService.getClusters(app, account))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "clusters-${app}-${account}"
      }
    }.toObservable()
  }

  Observable<Map> getCluster(String app, String account, String clusterName) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getCluster"))) {

      @Override
      protected Observable<Map> run() {
        Observable.from(oortService.getCluster(app, account, clusterName))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "clusters-${app}-${account}-${clusterName}"
      }
    }.toObservable()
  }

  Observable<Map> getClusterByType(String app, String account, String clusterName, String type) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getClusterByType"))) {

      @Override
      protected Observable<Map> run() {
        Observable.from(oortService.getClusterByType(app, account, clusterName, type))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "clusters-${app}-${account}-${clusterName}-${type}"
      }
    }.toObservable()
  }
}
