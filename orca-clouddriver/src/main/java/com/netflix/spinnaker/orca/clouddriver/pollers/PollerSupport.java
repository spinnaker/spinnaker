/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pollers;

import static java.lang.String.format;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import java.util.Optional;
import retrofit.RetrofitError;

public class PollerSupport {
  private final RetrySupport retrySupport;
  private final CloudDriverService cloudDriverService;

  public PollerSupport(RetrySupport retrySupport, CloudDriverService cloudDriverService) {
    this.retrySupport = retrySupport;
    this.cloudDriverService = cloudDriverService;
  }

  public Optional<ServerGroup> fetchServerGroup(String account, String region, String name) {
    return retrySupport.retry(
        () -> {
          try {
            ServerGroup response = cloudDriverService.getServerGroupTyped(account, region, name);
            return Optional.of(response);
          } catch (Exception e) {
            if (e instanceof RetrofitError) {
              RetrofitError re = (RetrofitError) e;
              if (re.getResponse() != null && re.getResponse().getStatus() == 404) {
                return Optional.empty();
              }
            }

            throw new PollerException(
                format(
                    "Unable to fetch server group (account: %s, region: %s, serverGroup: %s)",
                    account, region, name),
                e);
          }
        },
        5,
        2000,
        false);
  }

  static class PollerException extends RuntimeException {
    public PollerException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
