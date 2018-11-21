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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import retrofit.RetrofitError;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class CloudFoundryClientUtils {
  static void safelyCall(RetrofitConsumer r) throws CloudFoundryApiException {
    try {
      r.accept();
    } catch (RetrofitError retrofitError) {
      if (retrofitError.getResponse() == null || retrofitError.getResponse().getStatus() != 404) {
        throw new CloudFoundryApiException((ErrorDescription) retrofitError.getBodyAs(ErrorDescription.class));
      }
    }
  }

  static <T> Optional<T> safelyCall(RetrofitCallable<T> r) throws CloudFoundryApiException {
    try {
      return Optional.of(r.call());
    } catch (RetrofitError retrofitError) {
      if (retrofitError.getResponse() != null && retrofitError.getResponse().getStatus() == 404) {
        return Optional.empty();
      } else {
        throw new CloudFoundryApiException((ErrorDescription) retrofitError.getBodyAs(ErrorDescription.class));
      }
    }
  }

  static <R> List<R> collectPages(String resourceNamePluralized, Function<Integer, Pagination<R>> fetchPage) throws CloudFoundryApiException {
    Pagination<R> firstPage = safelyCall(() -> fetchPage.apply(null))
      .orElseThrow(() -> new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized));

    List<R> allResources = new ArrayList<>(firstPage.getResources());
    for (int page = 2; page <= Math.max(2, firstPage.getPagination().getTotalPages()); page++) {
      final int p = page;
      allResources.addAll(safelyCall(() -> fetchPage.apply(p))
        .orElseThrow(() -> new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized))
        .getResources());
    }

    return allResources;
  }

  static <R> List<Resource<R>> collectPageResources(String resourceNamePluralized, Function<Integer, Page<R>> fetchPage) throws CloudFoundryApiException {
    Page<R> firstPage = safelyCall(() -> fetchPage.apply(null))
      .orElseThrow(() -> new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized));

    List<Resource<R>> allResources = new ArrayList<>(firstPage.getResources());
    for (int page = 2; page <= Math.max(2, firstPage.getTotalPages()); page++) {
      final int p = page;
      allResources.addAll(safelyCall(() -> fetchPage.apply(p))
        .orElseThrow(() -> new CloudFoundryApiException("Unable to retrieve " + resourceNamePluralized))
        .getResources());
    }

    return allResources;
  }

  interface RetrofitCallable<T> {
    T call() throws RetrofitError;
  }

  interface RetrofitConsumer {
    void accept() throws RetrofitError;
  }
}
