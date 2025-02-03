/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.eureka.deploy.ops

import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import java.util.regex.Pattern

class EurekaUtil {

  static Eureka getWritableEureka(String endpoint, String region, ServiceClientProvider serviceClientProvider) {
    String eurekaEndpoint = endpoint.replaceAll(Pattern.quote('{{region}}'), region)
    serviceClientProvider.getService(Eureka, new DefaultServiceEndpoint("eureka", eurekaEndpoint))
  }

}
