/*
 * Copyright 2017 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.google;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest;
import com.netflix.spinnaker.clouddriver.google.security.AccountForClient;
import java.io.IOException;

/**
 * This class is syntactic sugar atop the static GoogleExecutor. By making it a trait, we can wrap
 * the calls with less in-line syntax.
 */
public interface GoogleExecutorTraits {

  String TAG_BATCH_CONTEXT = GoogleExecutor.getTAG_BATCH_CONTEXT();
  String TAG_REGION = GoogleExecutor.getTAG_REGION();
  String TAG_SCOPE = GoogleExecutor.getTAG_SCOPE();
  String TAG_ZONE = GoogleExecutor.getTAG_ZONE();
  String SCOPE_GLOBAL = GoogleExecutor.getSCOPE_GLOBAL();
  String SCOPE_REGIONAL = GoogleExecutor.getSCOPE_REGIONAL();
  String SCOPE_ZONAL = GoogleExecutor.getSCOPE_ZONAL();

  Registry getRegistry();

  default <T> T timeExecuteBatch(
      GoogleBatchRequest googleBatchRequest, String batchContext, String... tags)
      throws IOException {
    return GoogleExecutor.timeExecuteBatch(getRegistry(), googleBatchRequest, batchContext, tags);
  }

  default <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags)
      throws IOException {

    String account = AccountForClient.getAccount(request.getAbstractGoogleClient());
    String[] augmentedTags = new String[tags.length + 2];
    augmentedTags[0] = "account";
    augmentedTags[1] = account;
    System.arraycopy(tags, 0, augmentedTags, 2, tags.length);

    return GoogleExecutor.timeExecute(getRegistry(), request, "google.api", api, augmentedTags);
  }
}
