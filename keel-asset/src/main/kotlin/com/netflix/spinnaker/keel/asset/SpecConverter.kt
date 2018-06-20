/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.asset

import com.netflix.spinnaker.keel.AssetSpec
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.model.Job

interface SpecConverter<I : AssetSpec, S : Any> {

  fun convertToState(spec: I): S
  fun convertFromState(state: S): I?
  fun <C : ConvertToJobCommand<I>> convertToJob(command: C, changeSummary: ChangeSummary): List<Job>
}

interface ConvertToJobCommand<out S : AssetSpec> {
  val spec: S
}

data class DefaultConvertToJobCommand<out S : AssetSpec>(
  override val spec: S
) : ConvertToJobCommand<S>
