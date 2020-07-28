/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.concourse.client.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
public class Plan {
  private ObjectNode plan;

  public List<Resource> getResources() {
    return getResources(plan);
  }

  private List<Resource> getResources(ObjectNode o) {
    if (o.hasNonNull("id")) {
      if (o.hasNonNull("get")) {
        return Collections.singletonList(parseResource(o.get("id").asText(), o.get("get")));
      }
      if (o.hasNonNull("put")) {
        return Collections.singletonList(parseResource(o.get("id").asText(), o.get("put")));
      }
    }

    List<Resource> res = new ArrayList<>();

    Iterator<Entry<String, JsonNode>> fields = o.fields();
    while (fields.hasNext()) {
      Entry<String, JsonNode> f = fields.next();

      if (f.getValue().isArray()) {
        StreamSupport.stream(f.getValue().spliterator(), false)
            .filter(JsonNode::isObject)
            .map(jsonNode -> getResources((ObjectNode) jsonNode))
            .forEach(res::addAll);

      } else if (f.getValue().isObject()) {
        res.addAll(getResources((ObjectNode) f.getValue()));
      }
    }

    return res;
  }

  private Resource parseResource(String id, JsonNode res) {
    return new Resource(id, res.get("name").asText(), res.get("type").asText());
  }
}
