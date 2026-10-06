/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheResult
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheStatus
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CacheControllerSpec extends Specification {

  @Subject
  CacheController controller = new CacheController()

  @Unroll
  void "maps #cacheStatus on-demand cache results to #httpStatus"() {
    given:
    def updater = Mock(OnDemandCacheUpdater)
    controller.onDemandCacheUpdaters = [updater]

    when:
    def response = controller.handleOnDemand("gce", "LoadBalancer", [name: "listener"])

    then:
    1 * updater.handles(OnDemandType.LoadBalancer, "gce") >> true
    1 * updater.handle(OnDemandType.LoadBalancer, "gce", [name: "listener"]) >>
      new OnDemandCacheResult(cacheStatus, cachedIdentifiersByType)
    response.statusCode == httpStatus
    response.body == [cachedIdentifiersByType: cachedIdentifiersByType]

    where:
    cacheStatus                    | cachedIdentifiersByType                || httpStatus
    OnDemandCacheStatus.SUCCESSFUL | [:]                                    || HttpStatus.OK
    OnDemandCacheStatus.PENDING    | [loadBalancers: ["gce:listener:key"]] || HttpStatus.ACCEPTED
  }
}
