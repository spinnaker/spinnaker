/*
 * Copyright 2026 Moderne, Inc.
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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.pipeline.util.DockerLatestResolver;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves docker/image artifacts whose reference points at an ECR registry by delegating to
 * clouddriver's {@code /ecr/images/resolveDockerTag} (full URI) or {@code
 * /ecr/images/resolveDockerTagByName} (short {@code org/repo:tag} form) endpoints, which perform
 * the digest→semver-tag lookup using the existing AWS credentials repository.
 *
 * <p>Pipeline artifacts in moderne-saas use short-form references ({@code
 * moderne/recipe-worker-arm64:latest}) without the ECR registry hostname. Both forms are accepted
 * so that the resolver fires regardless of which form the pipeline stores.
 */
@Component
public class EcrDockerLatestResolver implements DockerLatestResolver {
  private static final Logger log = LoggerFactory.getLogger(EcrDockerLatestResolver.class);
  // Full ECR URI: 123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/repo:tag
  private static final Pattern ECR_FULL_REFERENCE =
      Pattern.compile("^(?:https?://)?\\d{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/.+:.+$");

  // Short ECR org reference as stored by moderne-saas pipelines: moderne/repo:tag
  private static final Pattern ECR_SHORT_REFERENCE = Pattern.compile("^moderne/[^:]+:.+$");

  private final OortService oortService;

  @Autowired
  public EcrDockerLatestResolver(OortService oortService) {
    this.oortService = oortService;
  }

  @Override
  public boolean handles(Artifact artifact) {
    String reference = artifact.getReference();
    if (reference == null) {
      return false;
    }
    return ECR_FULL_REFERENCE.matcher(reference).matches()
        || ECR_SHORT_REFERENCE.matcher(reference).matches();
  }

  @Override
  public Artifact canonicalize(Artifact artifact) {
    String reference = artifact.getReference();
    Map<String, String> response;
    if (ECR_SHORT_REFERENCE.matcher(reference).matches()) {
      // Split "moderne/repo:tag" into repository and tag for the by-name endpoint.
      int colon = reference.lastIndexOf(':');
      String repository = reference.substring(0, colon);
      String tag = reference.substring(colon + 1);
      try {
        response = Retrofit2SyncCall.execute(oortService.resolveDockerTagByName(repository, tag));
      } catch (SpinnakerHttpException e) {
        if (e.getResponseCode() == 404) {
          log.debug(
              "clouddriver resolveDockerTagByName endpoint not available (404); passing artifact through unresolved: {}",
              reference);
          return artifact;
        }
        throw e;
      }
    } else {
      response = Retrofit2SyncCall.execute(oortService.resolveDockerTag(reference));
    }
    String resolvedTag = response.get("resolvedTag");
    String resolvedReference = response.get("reference");
    if (resolvedTag == null || resolvedReference == null) {
      throw new IllegalStateException(
          "clouddriver resolveDockerTag returned an incomplete response: " + response);
    }
    return artifact.toBuilder().version(resolvedTag).reference(resolvedReference).build();
  }
}
