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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.api;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.Token;
import retrofit.Callback;
import retrofit.http.*;

import java.util.Map;

public interface AuthenticationService {
  @FormUrlEncoded
  @POST("/oauth/token")
  Token passwordToken(@Field("grant_type") String grantType, @Field("username") String username,
                      @Field("password") String password, @Field("client_id") String clientId,
                      @Field("client_secret") String clientSecret);

  @DELETE("/oauth/token/revoke/client/{clientId}")
  void revokeToken(@Path("clientId") String tokenId, Callback<Void> callback);

  @FormUrlEncoded
  @POST("/oath/authorize")
  void authorize(@Field("response_type") String responseType, @Field("client_id") String clientId,
                 @Field("scope") String scope, Callback<Map> callback);
}
