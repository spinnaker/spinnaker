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

package com.netflix.spinnaker.kato.orchestration

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Used to annotate an {@link AtomicOperationConverter} with the description of {@link AtomicOperation}
 * For example,
 * {@code
 *    @AtomicOperationDescription("destroyServerGroupDescription")
 *    class DestroyAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
 *      // ...
 *    }
 * }
 * @author sthadeshwar
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface AtomicOperationDescription {
  String value()
}