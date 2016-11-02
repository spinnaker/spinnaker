/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.endpoints.mvc

import com.netflix.spinnaker.rosco.endpoints.InstanceStatusEndpoint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody

@Component
class InstanceStatusMvcEndpoint extends EndpointMvcAdapter {

  @Autowired
  public InstanceStatusMvcEndpoint(InstanceStatusEndpoint delegate) {
    super(delegate)
  }

  @RequestMapping(value = '/instance', method = RequestMethod.GET)
  @ResponseBody
  @Override
  public Object invoke() {
    if (!getDelegate().isEnabled()) {
      return new ResponseEntity<Map<String, String>>(Collections.singletonMap(
          "message", "This endpoint is disabled"), HttpStatus.NOT_FOUND)
    }
    return super.invoke()
  }
}
