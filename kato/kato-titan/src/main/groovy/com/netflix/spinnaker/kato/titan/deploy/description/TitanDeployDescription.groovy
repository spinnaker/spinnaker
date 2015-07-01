/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.titan.deploy.description

import com.netflix.spinnaker.kato.deploy.DeployDescription
import groovy.transform.Canonical

/**
 * @author sthadeshwar
 */
class TitanDeployDescription extends AbstractTitanCredentialsDescription implements DeployDescription {
  String application
  String stack
  String details
  Source source = new Source()
  String subnetType
  String dockerImage
  Capacity capacity = new Capacity()
  String entryPoint
  int cpu
  int memory
  int disk
  int[] ports
  Map env
  int retries
  boolean restartOnSuccess

  @Canonical
  static class Capacity {
    int min
    int max
    int desired
  }

  @Canonical
  static class Source {
    String account
    String region
  }
}
