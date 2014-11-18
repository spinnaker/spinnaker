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
import org.springframework.stereotype.Service
import rx.Observable

@Service
class CredentialsService {
  private static final String SERVICE = "credentials"
  private static final HystrixCommandGroupKey HYSTRIX_KEY = HystrixCommandGroupKey.Factory.asKey(SERVICE)

  @Autowired
  KatoService katoService

  Observable<List> getAccountNames() {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getAccountNames"))) {

      @Override
      protected Observable<List> run() {
        Observable.from(katoService.accountNames)
      }

      @Override
      protected Observable<List> getFallback() {
        Observable.from([])
      }

      @Override
      protected String getCacheKey() {
        "accountNames-all"
      }
    }.toObservable()
  }

  Observable<Map> getAccount(String account) {
    new HystrixObservableCommand<Map>(HystrixObservableCommand.Setter.withGroupKey(HYSTRIX_KEY)
        .andCommandKey(HystrixCommandKey.Factory.asKey("getAccount"))) {

      @Override
      protected Observable<Map> run() {
        Observable.from(katoService.getAccount(account))
      }

      @Override
      protected Observable<Map> getFallback() {
        Observable.from([:])
      }

      @Override
      protected String getCacheKey() {
        "account-${account}"
      }
    }.toObservable()
  }
}
