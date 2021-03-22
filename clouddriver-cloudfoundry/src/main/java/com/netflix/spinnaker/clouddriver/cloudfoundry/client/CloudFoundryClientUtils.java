/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public final class CloudFoundryClientUtils {
  private static final ObjectMapper mapper =
      new ObjectMapper()
          .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public static <T> Optional<T> safelyCall(Supplier<Call<T>> r) {
    Response<T> response = null;
    try {
      response = r.get().execute();
    } catch (Exception e) {
      throw new CloudFoundryApiException(e);
    } finally {
      if (response != null && !response.isSuccessful()) {
        try (ResponseBody responseBody = response.errorBody()) {
          if (response.code() == 401) {
            throw new CloudFoundryApiException("Unauthorized");
          }
          if (response.code() == 404) {
            return Optional.empty();
          }
          ErrorDescription errorDescription =
              mapper.readValue(responseBody.string(), ErrorDescription.class);
          throw new CloudFoundryApiException(errorDescription);
        } catch (IOException e) {
          throw new CloudFoundryApiException(e, "Could not parse error");
        }
      }
    }
    return Optional.ofNullable(response.body());
  }

  static <R> List<R> collectPages(
      String resourceNamePluralized, Function<Integer, Call<Pagination<R>>> fetchPage)
      throws CloudFoundryApiException {
    Pagination<R> firstPage =
        safelyCall(() -> fetchPage.apply(null))
            .orElseThrow(
                () -> new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized));

    List<R> allResources = new ArrayList<>(firstPage.getResources());
    for (int page = 2; page <= firstPage.getPagination().getTotalPages(); page++) {
      final int p = page;
      allResources.addAll(
          safelyCall(() -> fetchPage.apply(p))
              .orElseThrow(
                  () ->
                      new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized))
              .getResources());
    }

    return allResources;
  }

  static <R> List<Resource<R>> collectPageResources(
      String resourceNamePluralized, Function<Integer, Call<Page<R>>> fetchPage)
      throws CloudFoundryApiException {
    Page<R> firstPage =
        safelyCall(() -> fetchPage.apply(null))
            .orElseThrow(
                () -> new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized));

    List<Resource<R>> allResources = new ArrayList<>(firstPage.getResources());
    for (int page = 2; page <= firstPage.getTotalPages(); page++) {
      final int p = page;
      allResources.addAll(
          safelyCall(() -> fetchPage.apply(p))
              .orElseThrow(
                  () ->
                      new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized))
              .getResources());
    }

    return allResources;
  }

  public static ObjectMapper getMapper() {
    return mapper;
  }
}
