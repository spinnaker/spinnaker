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
package com.netflix.spinnaker.keel.test

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareAssetSpec
import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.AssetSpec
import com.netflix.spinnaker.keel.AssetStatus
import com.netflix.spinnaker.keel.attribute.Attribute

@JsonTypeName("Test")
@JsonVersionedModel(currentVersion = "0", propertyName = "schema")
class TestAsset
@JsonCreator constructor(
  spec: TestAssetSpec,
  labels: MutableMap<String, String> = mutableMapOf(),
  attributes: MutableList<Attribute<*>> = mutableListOf()
) : Asset<TestAssetSpec>("1", "Test", spec, AssetStatus.ACTIVE, labels, attributes) {
  @JsonIgnore override val id = "test:${spec.id}"
}

// Using minimal class for ease in testing
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
abstract class TestAssetSpec: AssetSpec {
  abstract val id: String
}

data class GenericTestAssetSpec(
  override val id: String,
  val data: Map<String, Any> = mapOf()
) : TestAssetSpec()

data class ApplicationAwareTestAssetSpec(
  override val id: String,
  override val application: String
) : TestAssetSpec(), ApplicationAwareAssetSpec
