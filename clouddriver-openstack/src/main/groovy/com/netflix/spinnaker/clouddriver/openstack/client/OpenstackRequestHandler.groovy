/*
 * Copyright 2016 Target, Inc.
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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import org.openstack4j.model.common.ActionResponse

import java.lang.reflect.UndeclaredThrowableException

trait OpenstackRequestHandler {

  /**
   * Handler for an Openstack4J request with error common handling.
   * @param closure makes the needed Openstack4J request
   * @return returns the result from the closure
   */
  static <T> T handleRequest(Closure<T> closure) {
    T result
    try {
      result = closure()
    } catch (UndeclaredThrowableException e) {
      throw new OpenstackProviderException('Unable to process request', e.cause)
    } catch (OpenstackProviderException e) { //allows nested calls to handleRequest
      throw e
    } catch (Exception e) {
      throw new OpenstackProviderException('Unable to process request', e)
    }
    if (result instanceof ActionResponse && !result.isSuccess()) {
      throw new OpenstackProviderException(result)
    }
    result
  }

}
