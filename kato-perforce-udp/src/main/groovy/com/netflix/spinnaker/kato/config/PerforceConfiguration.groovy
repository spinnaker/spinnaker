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

package com.netflix.spinnaker.kato.config

import com.perforce.p4java.option.UsageOptions
import com.perforce.p4java.server.IOptionsServer
import com.perforce.p4java.server.ServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PerforceConfiguration {
  @Bean
  IOptionsServer p4server(PerforceProperties perforceProperties) {
    def p4server = ServerFactory.getOptionsServer("p4jrpc://${perforceProperties.host}:${perforceProperties.port}", null,
      new UsageOptions(null).setProgramName(perforceProperties.programName))
    p4server.userName = perforceProperties.userName
    p4server.connect()
    p4server
  }
}
