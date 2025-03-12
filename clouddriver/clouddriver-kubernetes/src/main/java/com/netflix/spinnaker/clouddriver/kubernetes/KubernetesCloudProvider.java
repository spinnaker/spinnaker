/*
 * Copyright 2015 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.kubernetes;

import com.netflix.spinnaker.clouddriver.core.CloudProvider;
import java.lang.annotation.Annotation;
import org.springframework.stereotype.Component;

/** Kubernetes declaration as a {@link CloudProvider}. */
@Component
public class KubernetesCloudProvider implements CloudProvider {
  public static final String ID = "kubernetes";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return "Kubernetes";
  }

  @Override
  public Class<? extends Annotation> getOperationAnnotationType() {
    return KubernetesOperation.class;
  }
}
