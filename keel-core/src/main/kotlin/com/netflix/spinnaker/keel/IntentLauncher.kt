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
package com.netflix.spinnaker.keel

import com.netflix.spinnaker.keel.exceptions.DeclarativeException

interface IntentLauncher<out R : LaunchedIntentResult> {

  fun launch(intent: Intent<IntentSpec>): R

  fun <I : Intent<IntentSpec>> intentProcessor(intentProcessors: List<IntentProcessor<*>>, intent: I)
    = intentProcessors.find { it.supports(intent) }.let {
    if (it == null) {
      throw DeclarativeException("Could not find processor for intent ${intent.javaClass.simpleName}")
    }
    // TODO rz - GROSS AND WRONG
    return@let it as IntentProcessor<I>
  }
}

interface LaunchedIntentResult
