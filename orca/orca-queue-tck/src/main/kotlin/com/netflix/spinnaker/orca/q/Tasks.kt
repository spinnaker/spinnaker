/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.SkippableTask
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

interface DummyTask : RetryableTask, SkippableTask
interface DummyCloudProviderAwareTask : RetryableTask, CloudProviderAware
interface InvalidTask : Task
interface DummyTimeoutOverrideTask : OverridableTimeoutRetryableTask
