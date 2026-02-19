/*
 * Copyright 2022 OpsMx Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun;

import com.netflix.spinnaker.clouddriver.core.CloudProvider;
import java.lang.annotation.Annotation;
import org.springframework.stereotype.Component;

/** Google Cloud Run declaration as a {@link CloudProvider}. */
@Component
public class CloudrunCloudProvider implements CloudProvider {
  public static final String ID = "cloudrun";
  final String id = ID;
  final String displayName = "Cloud run";
  final Class<? extends Annotation> operationAnnotationType = CloudrunOperation.class;

  /**
   * @return
   */
  @Override
  public String getId() {
    return id;
  }

  /**
   * @return
   */
  @Override
  public String getDisplayName() {
    return displayName;
  }

  /**
   * @return
   */
  @Override
  public Class<? extends Annotation> getOperationAnnotationType() {
    return operationAnnotationType;
  }
}
