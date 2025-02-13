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
package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
@Slf4j
class SnapshotService {

  @Autowired(required = false)
  Front50Service front50Service

  Map getCurrent(String application, String account) {
    Retrofit2SyncCall.execute(front50Service.getCurrentSnapshot("$application-$account"))
  }

  List<Map> getHistory(String application, String account, int limit) {
    Retrofit2SyncCall.execute(front50Service.getSnapshotHistory("$application-$account", limit))
  }
}
