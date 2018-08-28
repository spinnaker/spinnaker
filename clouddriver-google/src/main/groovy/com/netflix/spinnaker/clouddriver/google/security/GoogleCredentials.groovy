/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.security

import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.Compute
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import com.netflix.spinnaker.clouddriver.googlecommon.security.GoogleCommonCredentials
import groovy.transform.TupleConstructor

@TupleConstructor
public class GoogleCredentials extends GoogleCommonCredentials {
  final String project
  final ComputeVersion computeVersion

  Compute getCompute(String applicationName) {
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance()
    HttpTransport httpTransport = buildHttpTransport()

    def credential = getCredential(httpTransport, jsonFactory)
    def reqInit = setHttpTimeout(credential)
    def computeBuilder = new Compute.Builder(httpTransport, jsonFactory, credential)
        .setApplicationName(applicationName)
        .setHttpRequestInitializer(reqInit)

    if (computeVersion.servicePath) {
      computeBuilder.setServicePath(computeVersion.servicePath)
        .setBatchPath(computeVersion.batchPath)
    }

    return computeBuilder.build()
  }
}
