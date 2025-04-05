/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.igor.docker.service

import com.netflix.spinnaker.igor.docker.model.ClouddriverAccount
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/*
 * This service represents the interface with a simplified V2 docker registry service.
 * Specifically, the interface to docker registry images presented by clouddriver.
 */
interface ClouddriverService {
    @GET('dockerRegistry/images/find')
    Call<List<TaggedImage>> getImagesByAccount(@Query('account') String account, @Query('includeDetails') Boolean includeDetails)

    @GET('credentials')
    Call<List<ClouddriverAccount>> getAllAccounts()

    @GET('credentials/{account}')
    Call<Map> getAccountDetails(@Path('account') String account)
}
