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

package com.netflix.spinnaker.orca.retrofit.exceptions

import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import org.springframework.core.annotation.Order
import retrofit.RetrofitError
import retrofit.mime.TypedByteArray
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE

@Order(HIGHEST_PRECEDENCE)
class RetrofitExceptionHandler extends BaseRetrofitExceptionHandler {
  @Override
  boolean handles(Exception e) {
    return e.class == RetrofitError
  }

  @Override
  ExceptionHandler.Response handle(String stepName, Exception ex) {
    RetrofitError e = (RetrofitError) ex
    e.getResponse().with {
      Map<String, Object> responseDetails

      try {
        def body = e.getBodyAs(Map) as Map

        def error = body.error ?: reason
        def errors = body.errors ?: (body.messages ?: []) as List<String>
        errors = errors ?: (body.message ? [body.message] : [])

        responseDetails = ExceptionHandler.responseDetails(error, errors as List<String>)

        if (body.exception) {
          responseDetails.rootException = body.exception
        }
      } catch (ignored) {
        responseDetails = ExceptionHandler.responseDetails(properties.reason ?: e.message)
      }

      try {
        responseDetails.responseBody = new String(((TypedByteArray) e.getResponse().getBody()).getBytes())
      } catch (ignored) {
        responseDetails.responseBody = null
      }
      responseDetails.kind = e.kind
      responseDetails.status = properties.status ?: null
      responseDetails.url = properties.url ?: null

      return new ExceptionHandler.Response(e.class.simpleName, stepName, responseDetails, shouldRetry(e, e.kind.toString(), e.response?.status))
    }
  }
}
