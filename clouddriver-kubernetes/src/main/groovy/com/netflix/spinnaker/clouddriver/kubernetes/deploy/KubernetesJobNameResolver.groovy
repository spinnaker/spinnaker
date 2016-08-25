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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy

import com.netflix.frigga.NameBuilder

class KubernetesJobNameResolver extends NameBuilder {
  KubernetesJobNameResolver() {

  }

  String createJobName(String app, String stack, String detail) {
    def prefix = super.combineAppStackDetail(app, stack, detail).toString()
    def randString = Long.toHexString(Double.doubleToLongBits(Math.random()));
    return "$prefix-$randString"
  }
}
