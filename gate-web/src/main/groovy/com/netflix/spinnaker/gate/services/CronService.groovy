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
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

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
      echoService.validateCronExpression(cronExpression)
      return [ valid: true ]
    } catch (RetrofitError e) {
      if (e.response?.status == 400) {
        Map responseBody = e.getBodyAs(Map) as Map
        return [ valid: false, message: responseBody.message ]
      }
      throw e
    }
  }
}
