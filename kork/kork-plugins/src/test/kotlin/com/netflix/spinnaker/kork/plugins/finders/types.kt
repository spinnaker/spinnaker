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
package com.netflix.spinnaker.kork.plugins.finders

import org.pf4j.DefaultPluginDescriptor
import org.pf4j.Plugin

internal val pluginDescriptor = DefaultPluginDescriptor(
  "netflix/sentient-robot",
  "You pass the butter",
  Plugin::class.java.name,
  "0.0.1",
  "*",
  "Netflix",
  "Apache 2.0"
)
