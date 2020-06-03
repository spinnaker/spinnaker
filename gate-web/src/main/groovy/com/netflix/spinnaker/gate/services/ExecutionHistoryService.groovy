/*
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
@Slf4j
class ExecutionHistoryService {
  @Autowired
  OrcaServiceSelector orcaServiceSelector

  List getTasks(String app, Integer page, Integer limit, String statuses) {
    Preconditions.checkNotNull(app)

    orcaServiceSelector.select().getTasks(app, page, limit, statuses)
  }

  List getPipelines(String app, Integer limit, String statuses, Boolean expand) {
    Preconditions.checkNotNull(app)
    orcaServiceSelector.select().getPipelines(app, limit, statuses, expand)
  }
}
