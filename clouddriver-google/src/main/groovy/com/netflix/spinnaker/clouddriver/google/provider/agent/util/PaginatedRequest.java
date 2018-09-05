/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.google.provider.agent.util;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.netflix.spinnaker.clouddriver.google.provider.agent.AbstractGoogleCachingAgent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class PaginatedRequest<T> {
  private final AbstractGoogleCachingAgent cachingAgent;

  public PaginatedRequest(AbstractGoogleCachingAgent cachingAgent) {
    this.cachingAgent = cachingAgent;
  }

  public void queue(BatchRequest batchRequest, JsonBatchCallback<T> callback, String instrumentationContext) throws IOException {
    request(null).queue(batchRequest, new PaginatedCallback<T>(callback) {
      @Override
      protected void requestNextBatch(T t) throws IOException {
        String nextPageToken = getNextPageToken(t);
        BatchRequest batch = cachingAgent.buildBatchRequest();
        if (nextPageToken != null) {
          request(nextPageToken).queue(batch, this);
        }
        cachingAgent.executeIfRequestsAreQueued(batch, instrumentationContext);
      }
    });
  }

  public <U> List<U> timeExecute(Function<T, List<U>> itemExtractor, String api, String... tags) throws IOException {
    String pageToken = null;
    List<U> resultList = new ArrayList<>();
    do {
      T results = cachingAgent.timeExecute(request(pageToken), api, tags);
      resultList.addAll(itemExtractor.apply(results));
      pageToken = getNextPageToken(results);
    } while (pageToken != null);
    return resultList;
  }

  protected abstract String getNextPageToken(T t);
  protected abstract AbstractGoogleJsonClientRequest<T> request(String pageToken);
}
