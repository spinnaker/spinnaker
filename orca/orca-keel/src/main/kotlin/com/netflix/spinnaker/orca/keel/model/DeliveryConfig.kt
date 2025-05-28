/*
 * Copyright 2019 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.keel.model

/**
 * A Managed Delivery config file, modeled as a simple Map for now. Keel is expected to validate the format, not orca.
 * Added @JvnSuppressWildcards as Retrofit2 [com.netflix.spinnaker.orca.KeelService.publishDeliveryConfig]
 * does not support generic wildcards (?) in type declarations.
 */
typealias DeliveryConfig = Map<String, @JvmSuppressWildcards Any?>
