/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.retry;

import groovy.util.logging.Slf4j;
import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class RetryInterceptor implements Interceptor {
  private Logger logger = LoggerFactory.getLogger(RetryInterceptor.class);

  @Override
  public Response intercept(Chain chain) throws IOException {
    final String callName = "cf.api.call";
    final int maxAttempts = RetryConfig.ofDefaults().getMaxAttempts();
    AtomicInteger currentAttempts = new AtomicInteger();
    Retry retry =
        Retry.of(
            callName,
            RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(10), 3))
                .retryExceptions(SocketTimeoutException.class, RetryableApiException.class)
                .build());
    logger.trace("cf request: " + chain.request().url());
    AtomicReference<Response> lastResponse = new AtomicReference<>();
    try {
      return retry.executeCallable(
          () -> {
            currentAttempts.incrementAndGet();
            Response response = chain.proceed(chain.request());
            lastResponse.set(response);
            switch (response.code()) {
              case 502:
              case 503:
              case 504:
                // after retries fail, the response body for these status codes will get wrapped up
                // into a CloudFoundryApiException
                if (currentAttempts.get() < maxAttempts) {
                  response.close();
                }
                throw new RetryableApiException(
                    "Response Code "
                        + response.code()
                        + ": "
                        + chain.request().url()
                        + " attempting retry");
            }

            return response;
          });
    } catch (Exception e) {
      final Response response = lastResponse.get();
      if (response == null) {
        throw new IllegalStateException(e);
      }
      return response;
    }
  }

  private static class RetryableApiException extends RuntimeException {
    RetryableApiException(String message) {
      super(message);
    }
  }
}
