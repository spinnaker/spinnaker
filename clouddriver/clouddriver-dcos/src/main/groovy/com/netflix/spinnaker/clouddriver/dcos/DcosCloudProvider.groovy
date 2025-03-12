/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import org.springframework.stereotype.Component

import java.lang.annotation.Annotation

/**
 * Dcos declaration as a {@link CloudProvider}.
 */
@Component
class DcosCloudProvider implements CloudProvider {
  static final String ID = Keys.PROVIDER
  final String id = ID
  final String displayName = "Dcos"
  final Class<Annotation> operationAnnotationType = DcosOperation
}
