/*
 * Copyright 2020 Apple, Inc.
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

package com.netflix.spinnaker.igor.helm.accounts;

import java.util.List;
import java.util.Map;
import retrofit.http.*;

public interface HelmAccountsService {
  // Fetches Helm accounts from Clouddriver
  @GET("/artifacts/credentials")
  List<ArtifactAccount> getAllAccounts();

  @Headers("Content-Type: application/json")
  @PUT("/artifacts/fetch")
  String getIndex(@Body Map<String, String> body);
}
