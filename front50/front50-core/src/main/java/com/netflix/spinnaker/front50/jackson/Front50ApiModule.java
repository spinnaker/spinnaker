/*
 * Copyright 2021 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spinnaker.front50.api.model.Timestamped;
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.jackson.mixins.PipelineMixins;
import com.netflix.spinnaker.front50.jackson.mixins.TimestampedMixins;

public class Front50ApiModule extends SimpleModule {

  public Front50ApiModule() {
    super("Front50 API");
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.setMixInAnnotations(Pipeline.class, PipelineMixins.class);
    context.setMixInAnnotations(Timestamped.class, TimestampedMixins.class);
  }
}
