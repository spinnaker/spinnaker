/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArtifactMatcherTest {

  private static Map<String, Object> mapOf(Object... kv) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      map.put((String) kv[i], kv[i + 1]);
    }
    return map;
  }

  private final Map<String, Object> matchPayload =
      mapOf("one", "one", "two", "two", "three", "three");
  private final Map<String, Object> noMatchPayload = mapOf("four", "four", "five", "five");
  private final Map<String, Object> contstraints = mapOf("one", "one");
  private final Map<String, Object> shortConstraint = mapOf("one", "o");
  private final Map<String, Object> invalidConstraint = mapOf("one", "[one,two");
  private final Map<String, Object> constraintsOR = mapOf("one", List.of("uno", "one"));
  private final Map<String, Object> payloadWithList = mapOf("one", List.of("one"));
  private final Map<String, Object> stringifiedListConstraints = mapOf("one", "['uno', 'one']");
  private final Map<String, Object> jsonPathConstraintsToString = mapOf("$.one.test1.title", "st");
  private final Map<String, Object> jsonPathConstraintsToStringNOT =
      mapOf("$.one.test2.title", "no match");
  private final Map<String, Object> jsonPathConstraintsToBool =
      mapOf("$.one.test1.isValid", "true");
  private final Map<String, Object> jsonPathConstraintsToList =
      mapOf("$.one.test1.modified", ".yml");
  private final Map<String, Object> jsonPathConstraintsToListNOT =
      mapOf("$.one.test1.modified", ".xml");
  private final Map<String, Object> jsonPathConstraintsToObject =
      mapOf("$.one.test1.author", "edgar");
  private final Map<String, Object> jsonPathConstraintsToNumber = mapOf("$.two.test3.count", "3");
  private final Map<String, Object> jsonPathConstraintsToListOfObjects =
      mapOf("$.two.test2.changes", "bar");
  private final Map<String, Object> jsonPathConstraintsToUnknownField =
      mapOf("$.two.test4.changes", "bar");
  private final Map<String, Object> jsonPathConstraintsBadJsonPath =
      mapOf("$.one.test2.changes[a].value", "bar");
  private final Map<String, Object> multipleJsonPathConstraints =
      mapOf("$.one.test1.title", "st", "$.one.test2.title", "no match");

  private final Map<String, Object> complexPayload =
      mapOf(
          "one",
              mapOf(
                  "test1",
                      mapOf(
                          "title",
                          "test",
                          "modified",
                          List.of("folder1/file1.txt", "folder1/file2.txt", "folder2/file1.yml"),
                          "isValid",
                          true,
                          "author",
                          mapOf("name", "edgar", "username", "edgarulg")),
                  "test2",
                      mapOf(
                          "title", "another test",
                          "created", List.of("folder3/new.txt"),
                          "isValid", false,
                          "author", mapOf("name", "jorge", "username", "jorge123"),
                          "changes",
                              List.of(
                                  mapOf("id", 1, "value", "foo"), mapOf("id", 2, "value", "bar")))),
          "two", mapOf("test3", mapOf("count", 3, "ref", null, "isActive", false)));

  @Test
  void matchesWhenConstraintIsPartialWord() {
    assertThat(ArtifactMatcher.isConstraintInPayload(shortConstraint, matchPayload)).isTrue();
  }

  @Test
  void matchesExactString() {
    assertThat(ArtifactMatcher.isConstraintInPayload(contstraints, matchPayload)).isTrue();
  }

  @Test
  void noMatchWhenConstraintWordNotPresent() {
    assertThat(ArtifactMatcher.isConstraintInPayload(contstraints, noMatchPayload)).isFalse();
  }

  @Test
  void noMatchWhenConstraintInvalid() {
    assertThat(ArtifactMatcher.isConstraintInPayload(invalidConstraint, matchPayload)).isFalse();
  }

  @Test
  void matchesWhenPayloadValueIsInAListOfConstraintStrings() {
    assertThat(ArtifactMatcher.isConstraintInPayload(constraintsOR, matchPayload)).isTrue();
  }

  @Test
  void noMatchWhenValNotPresentInListOfConstraintStrings() {
    assertThat(ArtifactMatcher.isConstraintInPayload(constraintsOR, noMatchPayload)).isFalse();
  }

  @Test
  void matchesWhenValIsInStringifiedListOfConstraints() {
    assertThat(ArtifactMatcher.isConstraintInPayload(stringifiedListConstraints, matchPayload))
        .isTrue();
  }

  @Test
  void matchesWhenPayloadContainsListAndConstraintIsAStringifiedList() {
    assertThat(ArtifactMatcher.isConstraintInPayload(stringifiedListConstraints, payloadWithList))
        .isTrue();
  }

  @Test
  void matchesWhenPayloadIsAListListAndConstraintsAreAList() {
    assertThat(ArtifactMatcher.isConstraintInPayload(constraintsOR, payloadWithList)).isTrue();
  }

  @Test
  void matchesWhenConstraintIsPartialWordUsingJsonpath() {
    assertThat(ArtifactMatcher.isJsonPathConstraintInPayload(shortConstraint, matchPayload))
        .isTrue();
  }

  @Test
  void matchesExactStringUsingJsonpath() {
    assertThat(ArtifactMatcher.isJsonPathConstraintInPayload(contstraints, matchPayload)).isTrue();
  }

  @Test
  void noMatchWhenConstraintWordNotPresentUsingJsonpath() {
    assertThat(ArtifactMatcher.isJsonPathConstraintInPayload(contstraints, noMatchPayload))
        .isFalse();
  }

  @Test
  void noMatchWhenConstraintNotValidUsingJsonpath() {
    assertThat(ArtifactMatcher.isJsonPathConstraintInPayload(invalidConstraint, matchPayload))
        .isFalse();
  }

  @Test
  void matchesWhenPayloadValueIsInAListOfConstraintStringsUsingJsonpath() {
    assertThat(ArtifactMatcher.isJsonPathConstraintInPayload(constraintsOR, matchPayload)).isTrue();
  }

  @Test
  void noMatchWhenValNotPresentInListOfConstraintStringsUsingJsonpath() {
    assertThat(ArtifactMatcher.isJsonPathConstraintInPayload(constraintsOR, noMatchPayload))
        .isFalse();
  }

  @Test
  void matchesWhenValIsInStringifiedListOfConstraintsUsingJsonpath() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(stringifiedListConstraints, matchPayload))
        .isTrue();
  }

  @Test
  void matchesWhenPayloadContainsListAndConstraintIsAStringifiedListUsingJsonpath() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                stringifiedListConstraints, payloadWithList))
        .isTrue();
  }

  @Test
  void matchesWhenPayloadIsAListListAndConstraintsAreAListUsingJsonpath() {
    assertThat(ArtifactMatcher.isJsonPathConstraintInPayload(constraintsOR, payloadWithList))
        .isTrue();
  }

  @Test
  void matchesWhenValIsAStringUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToString, complexPayload))
        .isTrue();
  }

  @Test
  void noMatchWhenValIsAStringUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToStringNOT, complexPayload))
        .isFalse();
  }

  @Test
  void matchesWhenValIsABooleanUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToBool, complexPayload))
        .isTrue();
  }

  @Test
  void matchesWhenValIsANumberUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToNumber, complexPayload))
        .isTrue();
  }

  @Test
  void matchesWhenValIsAListOfStringUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToList, complexPayload))
        .isTrue();
  }

  @Test
  void noMatchesWhenValIsAListOfStringUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToListNOT, complexPayload))
        .isFalse();
  }

  @Test
  void noMatchWhenValIsAnObjectUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToObject, complexPayload))
        .isFalse();
  }

  @Test
  void noMatchWhenValIsAListOfMapUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToListOfObjects, complexPayload))
        .isFalse();
  }

  @Test
  void noMatchWhenFieldNotFoundUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsToUnknownField, complexPayload))
        .isFalse();
  }

  @Test
  void noMatchWhenBadExpressionUsingJSONPathConstraintWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                jsonPathConstraintsBadJsonPath, complexPayload))
        .isFalse();
  }

  @Test
  void noMatchWhenMultipleJSONPathConstraintsWithMultiLevelJson() {
    assertThat(
            ArtifactMatcher.isJsonPathConstraintInPayload(
                multipleJsonPathConstraints, complexPayload))
        .isFalse();
  }
}
