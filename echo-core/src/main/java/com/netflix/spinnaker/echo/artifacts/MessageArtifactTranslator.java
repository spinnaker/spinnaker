/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.echo.artifacts;

import com.netflix.spinnaker.echo.model.ArtifactEvent;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.parsing.ArtifactExtractor;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor;
import java.io.InputStream;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageArtifactTranslator {
  private final ArtifactExtractor artifactExtractor;
  private final ApplicationEventPublisher applicationEventPublisher;

  public List<Artifact> parseArtifacts(String messagePayload) {
    List<Artifact> artifacts = artifactExtractor.getArtifacts(messagePayload);
    if (!artifacts.isEmpty()) {
      applicationEventPublisher.publishEvent(new ArtifactEvent(null, artifacts));
    }
    return artifacts;
  }

  @Component
  @RequiredArgsConstructor
  public static class Factory {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JinjaArtifactExtractor.Factory jinjaExtractorFactory;

    public MessageArtifactTranslator create(ArtifactExtractor artifactExtractor) {
      return new MessageArtifactTranslator(artifactExtractor, applicationEventPublisher);
    }

    public MessageArtifactTranslator createJinja(String jinjaTemplate) {
      return this.create(jinjaExtractorFactory.create(jinjaTemplate));
    }

    public MessageArtifactTranslator createJinja(InputStream jinjaStream) {
      return this.create(jinjaExtractorFactory.create(jinjaStream));
    }
  }
}
