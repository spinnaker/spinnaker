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

package com.netflix.spinnaker.kato.docker.security

import com.netflix.spinnaker.amos.AccountCredentials

class DockerAccountCredentials implements AccountCredentials<Docker> {
  static final String PROVIDER = 'docker'
  String name
  String url
  String registry

  @Override
  Docker getCredentials() {
    new Docker(url, registry)
  }

  @Override
  String getProvider() {
    PROVIDER
  }

  @Override
  List<String> getRequiredGroupMembership() {
    []
  }
}
