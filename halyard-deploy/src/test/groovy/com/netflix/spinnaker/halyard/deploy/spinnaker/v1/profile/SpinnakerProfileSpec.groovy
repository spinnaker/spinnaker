/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile

import com.netflix.spinnaker.halyard.config.model.v1.node.*
import com.netflix.spinnaker.halyard.config.model.v1.problem.ConfigProblemSetBuilder
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact
import spock.lang.Specification

class SpinnakerProfileSpec extends Specification {
  final static String PATH = "/a/b/c/d/"

  void "find all required files referenced by config"() {
    setup:
    Node node = new NodeWithChildren()
    SpinnakerProfile c = new SpinnakerProfile() {
      @Override
      protected String commentPrefix() {
        return null
      }

      @Override
      String getProfileName() {
        return null
      }

      @Override
      SpinnakerArtifact getArtifact() {
        return null
      }

      @Override
      String getProfileFileName() {
        return null
      }
    }

    when:
    def files = c.dependentFiles(node)

    then:
    files.size() == 9
    files.every { x -> x.equals(PATH) }
  }

  class ChildNode extends Node {
    @Override
    void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    }

    @Override
    String getNodeName() {
      return null
    }

    @Override
    NodeIterator getChildren() {
      return NodeIteratorFactory.makeEmptyIterator()
    }

    @LocalFile path1 = PATH
    @LocalFile path2 = PATH
    @LocalFile path3 = PATH
    @LocalFile pathnull = null
  }

  class NodeWithChildren extends Node {
    @Override
    void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    }

    @Override
    String getNodeName() {
      return null
    }

    @Override
    NodeIterator getChildren() {
      return NodeIteratorFactory.makeReflectiveIterator(this)
    }

    ChildNode node1 = new ChildNode()
    ChildNode node2 = new ChildNode()
    ChildNode node3 = new ChildNode()
  }
}
