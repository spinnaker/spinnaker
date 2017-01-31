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

package com.netflix.spinnaker.halyard.config.spinnaker.v1.component

import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile
import com.netflix.spinnaker.halyard.config.model.v1.node.Node
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder
import spock.lang.Specification

import java.util.regex.Pattern

class SpinnakerComponentSpec extends Specification {
  final static String PATH = "/a/b/c/d/"

  void "correctly match all required profiles for a spring component"() {
    setup:
    String correctPath = "correct"
    String incorrectPath = "correct"

    File clouddriver1 = Mock(File)
    File clouddriver2 = Mock(File)
    File echo1 = Mock(File)
    File echo2 = Mock(File)
    File spinnaker = Mock(File)

    clouddriver1.getAbsolutePath() >> correctPath
    clouddriver1.getName() >> "clouddriver.yml"

    clouddriver2.getAbsolutePath() >> correctPath
    clouddriver2.getName() >> "clouddriver-profile.yml"

    echo1.getAbsolutePath() >> incorrectPath
    echo1.getName() >> "echo.yml"

    echo2.getAbsolutePath() >> incorrectPath
    echo2.getName() >> "echo-profile.yml"

    spinnaker.getAbsolutePath() >> correctPath
    spinnaker.getName() >> "spinnaker.yml"

    File[] files = [clouddriver1, clouddriver2, echo1, echo2, spinnaker]

    when:
    def result = (new ClouddriverComponent()).profilePaths(files)

    then:
    result.every { String t -> t.equals(correctPath) }
  }

  void "find all required files referenced by config"() {
    setup:
    Node node = new NodeWithChildren();
    SpinnakerComponent c = new SpinnakerComponent() {
      @Override
      protected String commentPrefix() {
        return null
      }

      @Override
      String getComponentName() {
        return null
      }

      @Override
      String getConfigFileName() {
        return null
      }

      @Override
      protected List<Pattern> profilePatterns() {
        return null
      }
    }

    when:
    def files = c.nodeFiles(node)

    then:
    files.size() == 9
    files.every { x -> x.equals(PATH) }
  }

  class ChildNode extends Node {
    @Override
    void accept(ProblemSetBuilder psBuilder, Validator v) {
    }

    @Override
    String getNodeName() {
      return null
    }

    @Override
    NodeIterator getChildren() {
      return NodeIteratorFactory.makeEmptyIterator()
    }

    @Override
    protected boolean matchesLocally(NodeFilter filter) {
      return false
    }

    @Override
    NodeFilter getFilter() {
      return null
    }

    @LocalFile path1 = PATH
    @LocalFile path2 = PATH
    @LocalFile path3 = PATH
    @LocalFile pathnull = null
  }

  class NodeWithChildren extends Node {
    @Override
    void accept(ProblemSetBuilder psBuilder, Validator v) {
    }

    @Override
    String getNodeName() {
      return null
    }

    @Override
    NodeIterator getChildren() {
      return NodeIteratorFactory.makeReflectiveIterator(this)
    }

    @Override
    protected boolean matchesLocally(NodeFilter filter) {
      return false
    }

    @Override
    NodeFilter getFilter() {
      return null
    }

    ChildNode node1 = new ChildNode()
    ChildNode node2 = new ChildNode()
    ChildNode node3 = new ChildNode()
  }
}
