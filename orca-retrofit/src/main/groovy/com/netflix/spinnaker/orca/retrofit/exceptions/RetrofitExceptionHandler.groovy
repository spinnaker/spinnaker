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

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import retrofit.RetrofitError
import retrofit.http.RestMethod
import retrofit.mime.TypedByteArray
import static java.net.HttpURLConnection.*
import static retrofit.RetrofitError.Kind.HTTP
import static retrofit.RetrofitError.Kind.NETWORK

class RetrofitExceptionHandler implements ExceptionHandler {
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
      boolean shouldRetry = ((isNetworkError(e) || isGatewayTimeout(e) || isThrottle(e)) && isIdempotentRequest(e))
      return new ExceptionHandler.Response(e.class.simpleName, stepName, responseDetails, shouldRetry)
    }
  }

  boolean isGatewayTimeout(RetrofitError e) {
    e.kind == HTTP && e.response.status in [HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT]
  }

  private static final int HTTP_TOO_MANY_REQUESTS = 429

  boolean isThrottle(RetrofitError e) {
    e.kind == HTTP && e.response.status == HTTP_TOO_MANY_REQUESTS
  }

  private boolean isNetworkError(RetrofitError e) {
    e.kind == NETWORK
  }

  private static boolean isIdempotentRequest(RetrofitError e) {
    findHttpMethodAnnotation(e) in ["GET", "HEAD", "DELETE", "PUT"]
  }

  private static String findHttpMethodAnnotation(RetrofitError exception) {
    exception.stackTrace.findResult { StackTraceElement frame ->
      try {
        Class.forName(frame.className)
          .interfaces
          .findResult { Class<?> iface ->
          iface.declaredMethods.findAll { Method m ->
            m.name == frame.methodName
          }.findResult { Method m ->
            m.declaredAnnotations.findResult { Annotation annotation ->
              annotation
                .annotationType()
                .getAnnotation(RestMethod)?.value()
            }
          }
        }
      } catch (ClassNotFoundException e) {
        // inner class or something non-accessible
        return null
      }
    }
  }
}
