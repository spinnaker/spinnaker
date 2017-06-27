/*
 * Copyright 2017 Cerner Corporation
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.util

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import mesosphere.marathon.client.model.v2.ExternalVolume
import mesosphere.marathon.client.model.v2.LocalVolume
import mesosphere.marathon.client.model.v2.PersistentLocalVolume
import mesosphere.marathon.client.model.v2.Volume

class MarathonVolumeDeserializer extends StdDeserializer<Volume> {

  MarathonVolumeDeserializer() {
    super(Volume.class)
  }

  @Override
  Volume deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec()
    TreeNode node = mapper.readTree(p)

    if (node.fieldNames().any { it == "external" }) {
      return mapper.treeToValue(node, ExternalVolume.class)
    } else if (node.fieldNames().any { it == "persistent" }) {
      return mapper.treeToValue(node, PersistentLocalVolume.class)
    }

    return mapper.treeToValue(node, LocalVolume.class)
  }
}
