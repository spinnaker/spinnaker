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

package com.netflix.spinnaker.clouddriver.core

import java.lang.annotation.Annotation

/**
 * Different cloud providers (AWS, GCE, Titus, etc.) should implement this interface and
 * annotate different implementations with annotation class indicated by {@code getAnnotation} method
 * to identify the cloud provider specific implementations
 *
 */
interface CloudProvider {

  /**
   * A unique string that identifies the cloud provider implementation
   * @return
   */
  String getId()

  /**
   * Display name or simply the name for the cloud provider. Use {@code getID()} for uniqueness constraints
   * instead of this method
   * @return
   */
  String getDisplayName()

  /**
   * Annotation type that can be assigned to the implementations for operations, converters, validators, etc. to enable
   * lookup based on the operation description name and cloud provider type
   * @return
   */
  Class<? extends Annotation> getOperationAnnotationType()

}
