/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline

// should contain a List<Stage> (let's not worry about tasks just yet)
// Stage interface <- abstract BatchStage <- impl classes
// BatchStage does the translation from our DSL to batch types (and back for monitoring?)
// stages can be persisted so we can easily retrieve a plan of the pipeline and current progress
interface Pipeline {

  String getId()

  List getTasks()

}
