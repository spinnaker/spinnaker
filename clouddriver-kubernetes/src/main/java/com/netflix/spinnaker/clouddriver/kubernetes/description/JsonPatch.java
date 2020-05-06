/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JsonPatch {
  Op op;
  String path;
  Object value;

  public enum Op {
    replace,
    add,
    remove
  }

  /**
   * Returns an escaped JSON path node for use in a JSON pointer as defined in RFC6901
   *
   * <p>~ is replaced by ~0 / is replaced by ~1
   *
   * @param node a node to be used as part of a JSON pointer
   * @return the node with escaped characters
   * @see <a href="https://tools.ietf.org/html/rfc6901#section-3">RFC6901, section 3</a>
   */
  public static String escapeNode(String node) {
    return node.replace("~", "~0").replace("/", "~1");
  }
}
