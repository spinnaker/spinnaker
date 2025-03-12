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

package com.netflix.spinnaker.halyard.config.model.v1

import com.netflix.spinnaker.halyard.config.model.v1.node.*
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder
import spock.lang.Specification

class NodeSpec extends Specification {
  static final List<String> field1Options = ["a", "b", "c"]

  class TestNode extends Node {
    List<String> field1Options(ConfigProblemSetBuilder p) {
      return field1Options
    }

    ChildTestNode node1 = new ChildTestNode("n1")
    ChildTestNode node2 = new ChildTestNode("n2")
    ChildTestNode node3 = new ChildTestNode("n3")

    @LocalFile String file1 = "/a/b/c/"
    @LocalFile String file2 = "/d/e/f/"

    @Override
    String getNodeName() {
      return "test"
    }
  }

  class ChildTestNode extends Node {
    String name
    List<Node> childNodes = new ArrayList<>()
    String field = "A"

    @Override
    String getNodeName() {
      return name
    }

    @Override
    NodeIterator getChildren() {
      return NodeIteratorFactory.makeListIterator(childNodes)
    }

    ChildTestNode(String name) {
      this.name = name
    }

    ChildTestNode() {
      this.name = "childtestnode"
    }
  }

  void "node reports matching field options"() {
    setup:
    def node = new TestNode()

    when:
    def options = node.fieldOptions(null, "field1")

    then:
    options == field1Options
  }

  void "node reports no matching field options"() {
    setup:
    def node = new TestNode()

    when:
    def options = node.fieldOptions(null, "field2")

    then:
    options == []
  }

  void "node correctly provides reflective iterator"() {
    setup:
    def node = new TestNode()
    def iterator = node.getChildren()

    when:
    def child = iterator.getNext()

    then:
    child != null
    while (child != null) {
      child.nodeName == new ChildTestNode().getNodeName()
      child = iterator.getNext()
    }
  }

  void "node correctly provides list iterator"() {
    setup:
    def node = new ChildTestNode()
    node.childNodes = [new ChildTestNode(), new ChildTestNode(), new ChildTestNode()]
    def iterator = node.getChildren()

    when:
    def child = iterator.getNext()
    def i = 0

    then:
    child != null
    while (child != null) {
      i++
      child = iterator.getNext()
    }

    i == node.childNodes.size()
  }

  void "node correctly reports localfiles"() {
    setup:
    def node = new TestNode()

    when:
    def files = node.localFiles()

    then:
    files.size() == 2
  }

  void "node correctly reports no localfiles"() {
    setup:
    def node = new ChildTestNode()

    when:
    def files = node.localFiles()

    then:
    files.size() == 0
  }

  void "node diff correctly reports no differences"() {
    setup:
    def n1 = new TestNode()
    def n2 = new TestNode()

    when:
    def diff1 = n1.diff(n2)
    def diff2 = n2.diff(n1)

    then:
    diff1 == null
    diff2 == null
  }

  void "node diff correctly reports file difference"() {
    setup:
    def n1 = new TestNode()
    def n2 = new TestNode()
    n1.file1 = "1"
    n2.file1 = "2"

    when:
    def diff1 = n1.diff(n2)

    then:
    diff1 != null
    diff1.changeType == NodeDiff.ChangeType.EDITED
    diff1.fieldDiffs.size() == 1
    diff1.fieldDiffs[0].fieldName == "file1"
    ((String) diff1.fieldDiffs[0].newValue) == n1.file1
    ((String) diff1.fieldDiffs[0].oldValue) == n2.file1
    diff1.nodeDiffs.size() == 0
  }

  void "node diff correctly reports child node edit"() {
    setup:
    def n1 = new TestNode()
    def n2 = new TestNode()
    n1.node3.field = "A"
    n2.node3.field = "B"

    when:
    def diff1 = n1.diff(n2)

    then:
    diff1 != null
    diff1.changeType == NodeDiff.ChangeType.EDITED
    diff1.fieldDiffs.size() == 0
    diff1.nodeDiffs.size() == 1
    diff1.nodeDiffs[0].location == n1.node3.name
    diff1.nodeDiffs[0].changeType == NodeDiff.ChangeType.EDITED
    diff1.nodeDiffs[0].fieldDiffs.size() == 1
    diff1.nodeDiffs[0].fieldDiffs[0].fieldName == "field"
  }

  void "node diff correctly child node addition"() {
    setup:
    def n1 = new ChildTestNode()
    def n2 = new ChildTestNode()
    n1.childNodes.add(new ChildTestNode("C"))

    when:
    def diff1 = n1.diff(n2)

    then:
    diff1 != null
    diff1.changeType == NodeDiff.ChangeType.EDITED
    diff1.fieldDiffs.size() == 0
    diff1.nodeDiffs.size() == 1
    diff1.nodeDiffs[0].location == "C"
    diff1.nodeDiffs[0].changeType == NodeDiff.ChangeType.ADDED
    diff1.nodeDiffs[0].fieldDiffs.size() == 0
  }

  void "node diff correctly child node removal"() {
    setup:
    def n1 = new ChildTestNode()
    def n2 = new ChildTestNode()
    n1.childNodes.add(new ChildTestNode("C"))

    when:
    def diff1 = n2.diff(n1)

    then:
    diff1 != null
    diff1.changeType == NodeDiff.ChangeType.EDITED
    diff1.fieldDiffs.size() == 0
    diff1.nodeDiffs.size() == 1
    diff1.nodeDiffs[0].location == "C"
    diff1.nodeDiffs[0].changeType == NodeDiff.ChangeType.REMOVED
    diff1.nodeDiffs[0].fieldDiffs.size() == 0
  }
}
