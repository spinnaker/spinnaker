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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Domain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

public interface DomainService {
  @GET("/v2/shared_domains/{guid}")
  Resource<Domain> findSharedDomainById(@Path("guid") String guid);

  @GET("/v2/private_domains/{guid}")
  Resource<Domain> findPrivateDomainById(@Path("guid") String guid);

  @GET("/v2/private_domains")
  Page<Domain> allPrivate(@Query("page") Integer page);

  @GET("/v2/shared_domains")
  Page<Domain> allShared(@Query("page") Integer page);
}
