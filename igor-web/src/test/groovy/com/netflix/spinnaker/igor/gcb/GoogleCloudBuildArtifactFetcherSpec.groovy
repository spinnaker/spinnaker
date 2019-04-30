/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb


import com.google.api.services.cloudbuild.v1.model.Build
import com.google.api.services.cloudbuild.v1.model.BuiltImage
import com.google.api.services.cloudbuild.v1.model.Results
import spock.lang.Specification

import java.util.stream.Collectors

class GoogleCloudBuildArtifactFetcherSpec extends Specification {
  static final String MANIFEST_BUCKET = "some-bucket"
  static final String MANIFEST_OBJECT = "artifacts-b3c0b5d3-c1c1-4ed3-b9e6-06651e04d4aa.json"
  static final String MANIFEST_PATH = "gs://some-bucket/artifacts-b3c0b5d3-c1c1-4ed3-b9e6-06651e04d4aa.json"

  GoogleCloudBuildClient client = Mock(GoogleCloudBuildClient)
  GoogleCloudBuildArtifactFetcher artifactFetcher = new GoogleCloudBuildArtifactFetcher(client)

  def "returns an empty array when there are no artifacts"() {
    given:
    Build build = new Build()

    when:
    def artifacts = artifactFetcher.getArtifacts(build)

    then:
    artifacts.size() == 0
  }

  def "correctly creates a docker artifact"() {
    given:
    BuiltImage image = new BuiltImage()
      .setName("gcr.io/my-project/my-container:latest")
      .setDigest("d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5")
    Build build = getBuild([image], false)

    when:
    def artifacts = artifactFetcher.getArtifacts(build)

    then:
    artifacts.size() == 1
    artifacts[0].name == "gcr.io/my-project/my-container"
    artifacts[0].version == "d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"
    artifacts[0].reference == "gcr.io/my-project/my-container@d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"
    artifacts[0].type == "docker/image"
  }

  def "correctly multiple a docker artifacts"() {
    given:
    List<BuiltImage> images = [
      new BuiltImage()
      .setName("gcr.io/my-project/my-container:latest")
      .setDigest("d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"),
      new BuiltImage()
        .setName("gcr.io/my-project/my-other-container:latest")
        .setDigest("edeaaff3f1774ad2888673770c6d64097e391bc362d7d6fb34982ddf0efd18cb")
    ]

    Build build = getBuild(images, false)

    when:
    def artifacts = artifactFetcher.getArtifacts(build)

    then:
    artifacts.size() == 2
    artifacts[0].name == "gcr.io/my-project/my-container"
    artifacts[0].version == "d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"
    artifacts[0].reference == "gcr.io/my-project/my-container@d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"
    artifacts[0].type == "docker/image"

    artifacts[1].name == "gcr.io/my-project/my-other-container"
    artifacts[1].version == "edeaaff3f1774ad2888673770c6d64097e391bc362d7d6fb34982ddf0efd18cb"
    artifacts[1].reference == "gcr.io/my-project/my-other-container@edeaaff3f1774ad2888673770c6d64097e391bc362d7d6fb34982ddf0efd18cb"
    artifacts[1].type == "docker/image"
  }

  def "returns no artifacts for an empty manifest"() {
    given:
    Build build = getBuild([], true)

    when:
    def artifacts = artifactFetcher.getArtifacts(build)

    then:
    client.fetchStorageObject(MANIFEST_BUCKET, MANIFEST_OBJECT) >> new ByteArrayInputStream()

    artifacts.size() == 0
  }

  def "correctly parses an artifact manifest with a single artifact"() {
    given:
    def gcsObjects = ["gs://artifact-bucket/test.out#1556268736494368"]
    Build build = getBuild([], true)

    when:
    def artifacts = artifactFetcher.getArtifacts(build)

    then:
    client.fetchStorageObject(MANIFEST_BUCKET, MANIFEST_OBJECT) >> new ByteArrayInputStream(getManifest(gcsObjects).getBytes())

    artifacts.size() == 1
    artifacts[0].name == "gs://artifact-bucket/test.out"
    artifacts[0].version == "1556268736494368"
    artifacts[0].reference == "gs://artifact-bucket/test.out#1556268736494368"
    artifacts[0].type == "gcs/object"
  }


  def "correctly parses an artifact manifest with a multiple artifacts"() {
    given:
    def gcsObjects = ["gs://artifact-bucket/test.out#1556268736494368", "gs://other-bucket/build.jar#2388597157"]
    Build build = getBuild([], true)

    when:
    def artifacts = artifactFetcher.getArtifacts(build)

    then:
    client.fetchStorageObject(MANIFEST_BUCKET, MANIFEST_OBJECT) >> new ByteArrayInputStream(getManifest(gcsObjects).getBytes())

    artifacts.size() == 2
    artifacts[0].name == "gs://artifact-bucket/test.out"
    artifacts[0].version == "1556268736494368"
    artifacts[0].reference == "gs://artifact-bucket/test.out#1556268736494368"
    artifacts[0].type == "gcs/object"

    artifacts[1].name == "gs://other-bucket/build.jar"
    artifacts[1].version == "2388597157"
    artifacts[1].reference == "gs://other-bucket/build.jar#2388597157"
    artifacts[1].type == "gcs/object"
  }

  def "can return both docker and gcs artifacts"() {
    given:
    BuiltImage image = new BuiltImage()
      .setName("gcr.io/my-project/my-container:latest")
      .setDigest("d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5")
    def gcsObjects = ["gs://artifact-bucket/test.out#1556268736494368"]
    Build build = getBuild([image], true)

    when:
    def artifacts = artifactFetcher.getArtifacts(build)

    then:
    client.fetchStorageObject(MANIFEST_BUCKET, MANIFEST_OBJECT) >> new ByteArrayInputStream(getManifest(gcsObjects).getBytes())

    artifacts.size() == 2

    artifacts[0].name == "gcr.io/my-project/my-container"
    artifacts[0].version == "d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"
    artifacts[0].reference == "gcr.io/my-project/my-container@d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"
    artifacts[0].type == "docker/image"

    artifacts[1].name == "gs://artifact-bucket/test.out"
    artifacts[1].version == "1556268736494368"
    artifacts[1].reference == "gs://artifact-bucket/test.out#1556268736494368"
    artifacts[1].type == "gcs/object"
  }

  Build getBuild(List<BuiltImage> images, boolean hasManifest) {
    Results results = new Results()

    if (images) {
      results.setImages(images)
    }

    if (hasManifest) {
      results.setArtifactManifest(MANIFEST_PATH)
    }

    return new Build().setResults(results)
  }

  String getManifest(List<String> paths) {
    List<String> fragments = paths.stream().map({p -> getManifestFragment(p)}).collect(Collectors.toList())
    return String.join("\n", fragments);
  }

  String getManifestFragment(String path) {
    return '{"location":"' + path + '","file_hash":[{"file_hash":[{"type":2,"value":"OE85d2dlRlRtc0FPOWJkaHRQc0Jzdz09"}]}]}'
  }
}
