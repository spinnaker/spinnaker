/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiFunction
import groovy.transform.CompileStatic
import groovy.transform.ToString
import com.fasterxml.jackson.annotation.JsonBackReference
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import static ExecutionStatus.NOT_STARTED

@CompileStatic
@ToString(includeNames = true)
abstract class AbstractStage<T extends Execution<T>> implements Stage<T>, Serializable {
  String id = UUID.randomUUID()
  String type
  String name
  Long startTime
  Long endTime
  ExecutionStatus status = NOT_STARTED
  @JsonBackReference T execution
  Map<String, Object> context = [:]
  final boolean immutable = false
  boolean initializationStage = false
  List<Task> tasks = []
  String parentStageId
  String refId
  Collection<String> requisiteStageRefIds = []
  SyntheticStageOwner syntheticStageOwner
  List<InjectedStageConfiguration> beforeStages = []
  List<InjectedStageConfiguration> afterStages = []
  long scheduledTime

  LastModifiedDetails lastModified

  @JsonIgnore
  AtomicInteger stageCounter = new AtomicInteger(0)

  @Deprecated
  @JsonIgnore
  AtomicInteger taskCounter = new AtomicInteger(0)

  @JsonIgnore
  StageNavigator stageNavigator = null

  transient ObjectMapper objectMapper = new OrcaObjectMapper()

  @JsonIgnore
  ObjectMapper getObjectMapper() {
    return this.objectMapper
  }

  /**
   * yolo
   */
  AbstractStage() {

  }

  AbstractStage(Execution execution, String type, String name, Map<String, Object> context) {
    this.execution = execution
    this.type = type
    this.name = name
    this.context = context

    this.refId = (String) context?.remove("refId")
    this.requisiteStageRefIds = (Collection<String>) context?.remove("requisiteStageRefIds")
  }

  AbstractStage(Execution execution, String type, Map<String, Object> context) {
    this(execution, type, null, context)
  }

  AbstractStage(Execution execution, String type) {
    this(execution, type, [:])
  }


  @Override
  public <O> O mapTo(String pointer, Class<O> type) {
    objectMapper.readValue(new TreeTraversingParser(getPointer(pointer ?: ""), objectMapper), type)
  }

  @Override
  void commit(String pointer, Object obj) {
    def rootNode = contextToNode()
    def ptr = getPointer(pointer, rootNode)
    if (ptr == null || ptr.isMissingNode()) {
      ptr = rootNode.setAll(createAndMap(pointer, obj))
    }
    mergeCommit ptr, obj
    context = (Map<String, Object>) objectMapper.convertValue(rootNode, LinkedHashMap)
  }

  @Override
  List<StageNavigator.Result> ancestors(BiFunction<Stage<T>, StageDefinitionBuilder, Boolean> matcher) {
    return (List<StageNavigator.Result>) (stageNavigator ? stageNavigator.findAll(this, matcher) :[])
  }

  private JsonNode getPointer(String pointer, ObjectNode rootNode = contextToNode()) {
    pointer ? rootNode.at(pointer) : rootNode
  }

  private ObjectNode contextToNode() {
    (ObjectNode)objectMapper.valueToTree(context)
  }

  private void mergeCommit(JsonNode node, Object obj) {
    merge objectMapper.valueToTree(obj), node
  }

  private ObjectNode createAndMap(String pointer, Object obj) {
    if (!pointer.startsWith("/")) {
      throw new IllegalArgumentException("Not allowed to create a root node")
    }
    def pathParts = pointer.substring(1).split("/").reverse() as Stack
    def node = objectMapper.createObjectNode()
    def last = expand(pathParts, node)
    mergeCommit(last, obj)
    node
  }

  private ObjectNode expand(Stack<String> path, ObjectNode node) {
    def ptr = path.pop()
    def next = objectMapper.createObjectNode()
    node.set(ptr, next)
    path.empty() ? next : expand(path, next)
  }

  private void merge(JsonNode sourceNode, JsonNode destNode) {
    Iterator<String> fieldNames = sourceNode.fieldNames()
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next()
      JsonNode sourceFieldValue = sourceNode.get(fieldName)
      JsonNode destFieldValue = destNode.get(fieldName)
      if (destFieldValue != null && destFieldValue.isObject()) {
        merge(sourceFieldValue, destFieldValue)
      } else if (destNode instanceof ObjectNode) {
        ((ObjectNode) destNode).replace(fieldName, sourceFieldValue)
      }
    }
  }

  static class LastModifiedDetails implements Serializable {
    String user
    Collection<String> allowedAccounts
    Long lastModifiedTime
  }
}
