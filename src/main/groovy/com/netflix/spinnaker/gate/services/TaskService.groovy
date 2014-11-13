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
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import rx.Observable

@CompileStatic
@Service
class TaskService {

  @Autowired
  OrcaService orcaService

  Observable<Map> create(Map body) {
    orcaService.doOperation(body).map({
      it
    })
  }

  // TODO Hystrix fallback?
  Observable<Map> getTask(String id) {
    Preconditions.checkNotNull(id)
    orcaService.getTask(id)
  }
}
