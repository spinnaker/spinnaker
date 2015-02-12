/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.front50.security

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.front50.model.application.GlobalAccountCredentials
import groovy.transform.Canonical

@Canonical
class CassandraCredentials implements AccountCredentials<Map>, GlobalAccountCredentials {
  static final String PROVIDER_TYPE = 'cassandra'
  String name

  @Override
  Map getCredentials() {
    return [:]
  }

  @Override
  String getProvider() {
    PROVIDER_TYPE
  }
}
