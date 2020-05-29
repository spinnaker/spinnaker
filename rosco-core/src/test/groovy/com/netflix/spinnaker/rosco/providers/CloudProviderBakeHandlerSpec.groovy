/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.rosco.providers

import com.google.common.base.Strings
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CloudProviderBakeHandlerSpec extends Specification implements TestDefaults {


  void 'we should create a fully decorated artifact if all base data is present'() {
    setup:
      def expectedArtifact = Artifact.builder()
        .name(SOME_BAKE_RECIPE.name)
        .version(null)
        .location(SOME_REGION)
        .type("${SOME_CLOUD_PROVIDER}/image")
        .reference(SOME_BAKE_DETAILS.ami)
        .metadata([
          build_info_url: SOME_BAKE_REQUEST.build_info_url,
          build_number: SOME_BAKE_REQUEST.build_number])
        .uuid(SOME_BAKE_DETAILS.id)
        .build()

      @Subject
      CloudProviderBakeHandler bakeHandler = Spy(CloudProviderBakeHandler)

    when:
      Artifact producedArtifact = bakeHandler.produceArtifactDecorationFrom(SOME_BAKE_REQUEST, SOME_BAKE_RECIPE, SOME_BAKE_DETAILS, SOME_CLOUD_PROVIDER, SOME_REGION)

    then:
      producedArtifact == expectedArtifact
  }

  @Unroll
  void 'we should not fail if data is missing for artifact decoration'() {
    expect:
      @Subject
      CloudProviderBakeHandler bakeHandler = Spy(CloudProviderBakeHandler)
      def decoratedArtifact = bakeHandler.produceArtifactDecorationFrom(bakeRequest, bakeRecipe, SOME_BAKE_DETAILS, SOME_CLOUD_PROVIDER, SOME_REGION)
      Strings.emptyToNull(decoratedArtifact.name) == expectedName
      Strings.emptyToNull(decoratedArtifact.version) == expectedVersion
      decoratedArtifact.reference == expectedReference
      decoratedArtifact.metadata == expectedMetadata

    where:
      bakeRequest       | bakeRecipe       | expectedName          | expectedVersion      | expectedReference | expectedMetadata
      null              | null             | null                  | null                 | SOME_AMI_ID       | ["build_info_url": null, "build_number": null]
      null              | SOME_BAKE_RECIPE | SOME_BAKE_RECIPE_NAME | null                 | SOME_AMI_ID       | ["build_info_url": null, "build_number": null]
      SOME_BAKE_REQUEST | SOME_BAKE_RECIPE | SOME_BAKE_RECIPE_NAME | null                 | SOME_AMI_ID       | ["build_info_url": SOME_BUILD_INFO_URL, "build_number": SOME_BUILD_NR]

  }

}
