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

import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Slf4j
class CronService {
  @Autowired(required=false)
  EchoService echoService

  Map validateCronExpression(String cronExpression) {
    if (!echoService) {
      return [ valid: false, message: 'No echo service available' ]
    }

    try {
      Map validationResult = Retrofit2SyncCall.execute(echoService.validateCronExpression(cronExpression))
      return [ valid: true, description: validationResult.description ]
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 400) {
        return [ valid: false, message: e.message ]
      }
      throw e
    }
  }
}
