/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.rosco.manifests.helm;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses Helm set arguments from a string input. This class is inspired by the Helm's Go code for
 * parsing --set arguments,
 *
 * @link <a href="https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go">...</a>
 *     specifically designed to handle complex nested structures as defined in Helm set arguments.
 *     It supports parsing into a map structure, handling lists, nested maps, and basic types.
 *     <p>For example, given the --set input as a single comma-separated string:
 *     "name1=value1,server.port=8080,servers[0].ip=192.168.1.1,servers[0].dns.primary=8.8.8.8,hosts={host1,host2,host3}"
 *     <p>The corresponding map output would be: { "name1": "value1", "server": {"port": "8080"},
 *     "servers": [{"ip": "192.168.1.1", "dns": {"primary": "8.8.8.8"}}], "hosts": ["host1",
 *     "host2", "host3"] }
 */
public class HelmSetArgumentParser {
  /**
   * The maximum index allowed for list elements in helm3's set arguments
   * https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L36
   */
  private static final int MAX_INDEX = 65536;
  /**
   * The maximum level of nesting allowed for names within the helm3's set arguments.
   * https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L40
   */
  private static final int MAX_NESTED_NAME_LEVEL = 30;

  private final PushbackReader stringReader;
  /**
   * Specifies whether all parsed values should be treated as strings, ignoring any potential for
   * type inference based on the value content. When true, the parser does not attempt to infer
   * types like integers, booleans, or nulls, treating every value as a plain string instead.
   */
  private final boolean treatValuesAsString;

  /**
   * Initializes a parser for Helm set arguments from a string, supporting complex structures.
   *
   * @param helmSetArguments A string containing the Helm set arguments to be parsed. The string
   *     should follow the Helm convention for specifying --set values, which includes the ability
   *     to define nested properties and lists. For example,
   *     "key1=value1,key2.subkey1=value2,key3[0]=value3,key4={1,2,3}" is a valid format.
   * @param treatValuesAsString Indicates whether to treat all parsed values strictly as strings
   *     (true), or to attempt to infer their actual data types (false), enhancing type accuracy.
   */
  public HelmSetArgumentParser(String helmSetArguments, boolean treatValuesAsString) {
    stringReader = new PushbackReader(new StringReader(helmSetArguments));
    this.treatValuesAsString = treatValuesAsString;
  }

  /**
   * Parses the input string into a structured map of Helm set arguments. It processes the string
   * until the end, handling nested structures and type conversions as necessary.
   *
   * <p>Ref: Ref: https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L158
   *
   * @return A map representing the structured Helm set arguments.
   * @throws IOException If parsing fails due to an unexpected read error.
   */
  public Map<String, Object> parse() throws IOException {
    Map<String, Object> helmArgumentMap = new HashMap<>();
    int currentChar;
    while ((currentChar = stringReader.read()) != -1) {
      stringReader.unread(currentChar);
      // ignoring the return type as return type false is indicative of eof while processing the
      // input argument
      // original helm code ignores eof, and returns the populated map.
      // for example key1=value1,key2= , will update the helmArgumentMap as
      // {<key1,value1>,<key2,"">}
      key(helmArgumentMap, 0);
    }
    return helmArgumentMap;
  }

  /**
   * Processes and adds an item to a list based on its index, handling nested structures and type
   * conversions. The method supports direct value assignment, nested list handling, and nested map
   * handling based on the encountered stop character ('=', '[', '.'). It ensures the list is
   * correctly expanded and populated.
   *
   * <p>Ref: https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L335
   *
   * @param helmValuesList The list to which the item should be added or within which an item should
   *     be modified.
   * @param index The index at which the item should be added or modified.
   * @param nestedNameLevel The current level of nesting, used to prevent excessive nesting depths.
   * @return The modified list with the new item added or an existing item updated.
   * @throws IllegalArgumentException If a negative index is provided or unexpected data is
   *     encountered.
   * @throws IOException If parsing fails due to other reasons, including IOExceptions from
   *     underlying reads.
   */
  private List<Object> listItem(List<Object> helmValuesList, int index, int nestedNameLevel)
      throws IOException {
    if (index < 0) {
      throw new IllegalArgumentException("negative " + index + " index not allowed");
    }
    Set<Character> stopCharacters = Set.of('[', '.', '=');
    Result readResult = readCharactersUntil(stopCharacters);
    if (!readResult.value.isEmpty())
      throw new IllegalArgumentException(
          "unexpected data at end of array index " + readResult.value);
    if (readResult.isEof()) {
      // example argument - "noval[0]"
      return helmValuesList;
    }
    switch (readResult.stopCharacter.get()) {
      case '=':
        try {
          Optional<List<Object>> valueListResultOptional = valList();
          if (valueListResultOptional.isEmpty()) {
            // example argument - "noval[0]="
            return setIndex(helmValuesList, index, "");
          } else {
            return setIndex(helmValuesList, index, valueListResultOptional.get());
          }
        } catch (NotAListException notAListException) {
          return setIndex(helmValuesList, index, typedVal(val().value));
        }
      case '[':
        List<Object> currentItems = new ArrayList<>();
        int nextIndex = keyIndex();
        /*
        Consider the scenario key[1][2]=value1, key[1][1]=value2 with an initially empty map.
        For key[1][2]=value1:

        The parser identifies that it needs to access index 1 of helmValuesList for key1 which doesn't exist yet. Thus, it expands the list: helmValuesList = [null, []].
        Then, it needs to set value1 at index 2 of this nested list. Since the nested list (currentItems) is empty, it's expanded to accommodate index 2: currentItems = [null, null, "value1"].

        For key[1][1]=value2 (continuing from the previous state):

        The parser again targets index 1 of helmValuesList for key, which now exists and contains [null, null, "value1"].
        To set value2 at index 1, it updates the existing nested list, resulting in currentItems = [null, "value2", "value1"].
        */
        if (helmValuesList.size() > index && helmValuesList.get(index) != null) {
          currentItems = (List<Object>) helmValuesList.get(index);
        }

        List<Object> nestedListItems = listItem(currentItems, nextIndex, nestedNameLevel);
        return setIndex(helmValuesList, index, nestedListItems);
      case '.':
        Map<String, Object> nestedMap = new HashMap<>();
        if (helmValuesList.size() > index) {
          Object currentElement = helmValuesList.get(index);
          if (currentElement != null) {
            // If the current element is already a map, use it
            nestedMap = (Map<String, Object>) currentElement;
          }
        }

        boolean res = key(nestedMap, nestedNameLevel);
        if (!res) {
          // example argument - "a[0]."
          return helmValuesList;
        }
        return setIndex(helmValuesList, index, nestedMap);

      default:
        throw new IllegalArgumentException(
            "parse error: unexpected token " + readResult.stopCharacter);
    }
  }

  /**
   * Parses a key from the input and updates the provided parsedValuesMap map based on the key's
   * type and value. This method handles different structures such as lists and nested maps, and it
   * populates the map accordingly. It deals with keys ending in specific characters ('=', '[', ',',
   * '.') to determine the structure type and recursively parses nested structures as needed.
   *
   * <p>Ref: https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L179
   *
   * @param parsedValuesMap The map to be updated with the parsed key and its value.
   * @param nestingDepth The current depth of nested keys, used to prevent excessively deep nesting.
   * @return true when the argument was processed completely and no eof was encountered. For
   *     example, "key1=value1" would return true as it is a complete key-value pair. Returns false
   *     when the argument was processed completely but ended with eof. For example, "key1=" would
   *     return false as it signifies an incomplete value, updating parsedValuesMap with {key1, ""}.
   * @throws IllegalArgumentException If a key without a value is encountered or if the nesting
   *     level exceeds the maximum allowed depth.
   * @throws IOException If parsing the key or its value fails due to other reasons.
   */
  private boolean key(Map<String, Object> parsedValuesMap, int nestingDepth) throws IOException {
    Set<Character> stopCharacters = Set.of('=', '[', ',', '.');
    while (true) {
      Result readResult = readCharactersUntil(stopCharacters);
      if (readResult.isEof()) {
        if (StringUtils.isEmpty(readResult.value)) {
          return false;
        }
        throw new IllegalArgumentException("key " + readResult.value + " has no value");
      }
      switch (readResult.stopCharacter.get()) {
        case '[':
          {
            int keyIndex = keyIndex();
            List<Object> currentList;
            if (parsedValuesMap.containsKey(readResult.value)
                && parsedValuesMap.get(readResult.value) != null) {
              // Helm does not allow keys with value types as primitives or lists to be assigned a
              // map value.
              // For example, using the command `helm template test <chart> --set a=1,a.b=1` results
              // in an error:
              // Error: failed parsing --set data: unable to parse key: interface conversion:
              // interface {} is int64, not map[string]interface {}.
              // The code below throws a class exception to make the behavior consistent.

              currentList = (List<Object>) parsedValuesMap.get(readResult.value);
            } else {
              currentList = new ArrayList<>();
            }

            List<Object> currentListTmp = listItem(currentList, keyIndex, nestingDepth);
            set(parsedValuesMap, readResult.value, currentListTmp);

            return true;
          }
        case '=':
          {
            Optional<List<Object>> valuesList;
            try {
              valuesList = valList();
              if (valuesList.isEmpty()) {
                set(parsedValuesMap, readResult.value, "");
                return false;
              } else {
                set(parsedValuesMap, readResult.value, valuesList.get());
              }
            } catch (NotAListException notAListException) {
              Result singleValueResult = val();
              set(parsedValuesMap, readResult.value, typedVal(singleValueResult.value));
            }
            return true;
          }
        case ',':
          {
            set(parsedValuesMap, readResult.value, "");
            throw new IllegalArgumentException(
                String.format("key %s has no value (cannot end with ,)", readResult.value));
          }
        case '.':
          {
            if (nestingDepth++ > MAX_NESTED_NAME_LEVEL) {
              throw new IllegalArgumentException(
                  "Value name nested level is greater than maximum supported nested level of "
                      + MAX_NESTED_NAME_LEVEL);
            }
            Map<String, Object> nestedMap = new HashMap<>();
            if (parsedValuesMap.containsKey(readResult.value)
                && parsedValuesMap.get(readResult.value) != null) {
              // Helm does not allow keys with value types as primitives or lists to be assigned a
              // map value.
              // For example, using the command `helm template test <chart> --set a=1,a.b=1` results
              // in an error:
              // Error: failed parsing --set data: unable to parse key: interface conversion:
              // interface {} is int64, not map[string]interface {}.
              // The code below throws a class exception to make the behavior consistent.

              nestedMap = (Map<String, Object>) parsedValuesMap.get(readResult.value);
            }
            boolean res = key(nestedMap, nestingDepth);

            if (res && nestedMap.isEmpty()) {
              // test data "name1.=name2"
              throw new IllegalArgumentException("key map " + readResult.value + " has no value");
            }
            if (!nestedMap.isEmpty()) {
              parsedValuesMap.put(readResult.value, nestedMap);
            }
            return res;
          }
        default:
          throw new IllegalArgumentException(
              "parse error: unexpected token " + readResult.stopCharacter);
      }
    }
  }

  private void set(Map<String, Object> targetMap, String key, Object valueToSet) {
    if (key == null || key.isEmpty()) {
      return;
    }
    targetMap.put(key, valueToSet);
  }

  /**
   * Inserts or updates a value at a specified index within a list. If the index exceeds the current
   * list size, the list is expanded with null elements to accommodate the new index. This method
   * ensures the index is within a predefined range to prevent excessive list sizes.
   *
   * <p>Ref: https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L299
   *
   * @param targetList The list to be modified.
   * @param index The position at which to insert or update the value.
   * @param value The value to insert or update at the specified index.
   * @return The modified list with the value set at the specified index.
   * @throws IllegalArgumentException if the index is negative or exceeds the maximum allowed index.
   */
  private List<Object> setIndex(List<Object> targetList, int index, Object value)
      throws IOException {
    if (index < 0) {
      throw new IllegalArgumentException("negative " + index + " index not allowed");
    }
    if (index > MAX_INDEX) {
      throw new IllegalArgumentException(
          "index of " + index + " is greater than maximum supported index of " + MAX_INDEX);
    }

    // Ensuring the list is large enough
    while (targetList.size() <= index) {
      targetList.add(null);
    }

    targetList.set(index, value);
    return targetList;
  }

  /**
   * Parses the next segment of input as an index, expecting the segment to terminate with a ']'
   * character. This method is used to parse array-like index specifications in the input string.
   *
   * <p>Ref: https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L324
   *
   * @return The parsed index as an integer.
   * @throws IOException If an I/O error occurs during reading.
   * @throws IllegalArgumentException If the end of the input is reached unexpectedly or if the
   *     parsed index is not a valid integer.
   */
  private int keyIndex() throws IOException {
    Set<Character> stopCharacters = Set.of(']');
    Result readResult = readCharactersUntil(stopCharacters);
    if (readResult.isEof()) {
      throw new IllegalArgumentException(
          "Error parsing index: Expected closing bracket ']', but encountered EOF");
    }
    int parsedIndex;
    try {
      parsedIndex = Integer.parseInt(readResult.value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Error parsing index: parsing '" + readResult.value + "': invalid syntax", e);
    }
    return parsedIndex;
  }

  private Result val() throws IOException {
    Set<Character> stopCharacters = Set.of(',');
    return readCharactersUntil(stopCharacters);
  }

  /**
   * Parses a list of values enclosed in curly braces from the input stream. This method is designed
   * to read and interpret a structured list format, expecting values to be separated by commas and
   * the entire list to be enclosed within '{' and '}'. Each value within the list is parsed
   * according to its type, and the method supports nested lists and objects as elements of the
   * outer list.
   *
   * <p>Ref: https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L465
   *
   * @return An Optional containing a list of parsed objects, each corresponding to a value found
   *     within the input list. Returns an empty Optional if the initial character indicates an EOF,
   *     signifying that no list is present at the expected location.
   * @throws IOException If an I/O error occurs during reading.
   * @throws NotAListException If the initial character read is not '{', indicating that the
   *     expected list structure is not present.
   * @throws IllegalArgumentException If the list structure is malformed, such as lacking a closing
   *     '}'.
   */
  private Optional<List<Object>> valList() throws IOException {
    int currentChar = stringReader.read();
    if (currentChar == -1) {
      // example argument - "name1.name2="
      return Optional.empty();
    }

    if ((char) currentChar != '{') {
      stringReader.unread(currentChar);
      throw new NotAListException("not a list");
    }

    List<Object> valueList = new ArrayList<>();
    Set<Character> stopCharacters = Set.of(',', '}');
    while (true) {
      Result readResult = readCharactersUntil(stopCharacters);
      if (readResult.isEof()) {
        throw new IllegalArgumentException("list must terminate with '}'");
      }
      switch (readResult.stopCharacter.get()) {
        case '}':
          {
            int nextChar = stringReader.read();
            if (nextChar != -1 && (char) nextChar != ',') {
              stringReader.unread(nextChar);
            }
            valueList.add(typedVal(readResult.value));
            return Optional.of(valueList);
          }

        case ',':
          valueList.add(typedVal(readResult.value));
          break;
        default:
          throw new IllegalArgumentException(
              "parse error: unexpected token " + readResult.stopCharacter);
      }
    }
  }

  /**
   * Reads characters from the input stream until a stopCharacters character is encountered. This
   * method parses input, accumulating characters into a string until one of the specified
   * stopCharacters characters is read. It handles escape sequences by including the character
   * immediately following a backslash in the output without evaluation. Ref :
   * https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L503
   *
   * @param stopCharacters A set of characters that should terminate the reading.
   * @return A Result object containing the accumulated string up to (but not including) the
   *     stopCharacters character, the stopCharacters character itself, and a flag indicating if the
   *     end of the stream (EOF) was reached.
   * @throws IOException If an I/O error occurs during reading.
   */
  private Result readCharactersUntil(Set<Character> stopCharacters) throws IOException {
    StringBuilder accumulatedChars = new StringBuilder();
    int r;
    while ((r = stringReader.read()) != -1) {
      char currentChar = (char) r;
      if (stopCharacters.contains(currentChar)) {
        return new Result(accumulatedChars.toString(), currentChar);
      }
      // backslash is the escape character to include special characters like period,comma etc.
      // Treat the next character as a literal part of the value, bypassing its special meaning
      else if (currentChar == '\\') {

        int nextCharCode = stringReader.read();
        if (nextCharCode == -1) {
          // example argument - "key=value\\"
          return new Result(accumulatedChars.toString());
        }
        accumulatedChars.append((char) nextCharCode);
      } else {
        accumulatedChars.append(currentChar);
      }
    }
    return new Result(accumulatedChars.toString());
  }

  /**
   * Represents the outcome of reading characters from an input stream until a specified stop
   * character or end of input. This class captures the accumulated characters as a string. The stop
   * character is optional and will be absent if the reading stopped because the end of the input
   * (EOF) was reached.
   */
  class Result {

    private final String value;
    private final Optional<Character> stopCharacter;

    public Result(String value, Character stopCharacter) {
      this.value = value;
      this.stopCharacter = Optional.of(stopCharacter);
    }

    public Result(String value) {
      this.value = value;
      this.stopCharacter = Optional.empty();
    }

    public boolean isEof() {
      return stopCharacter.isEmpty();
    }
  }
  /**
   * Converts a string value to its corresponding Java type based on its content. If the {@code
   * treatValuesAsString} flag is set, all values are returned as strings. Otherwise, it tries to
   * convert the string to a boolean, null, long, or falls back to returning the string itself if no
   * specific type conversion is applicable.
   *
   * <p>Ref : https://github.com/helm/helm/blob/v3.14.2/pkg/strvals/parser.go#L528
   *
   * @param value The string representation of the value to be converted.
   * @return The converted value as an Object. This can be a String, Boolean, Long, or null,
   *     depending on the content of the input string and the value of {@code treatValuesAsString}.
   */
  private Object typedVal(String value) {

    if (treatValuesAsString) {
      return value;
    }

    if (value.equalsIgnoreCase("true")) {
      return Boolean.TRUE;
    }

    if (value.equalsIgnoreCase("false")) {
      return Boolean.FALSE;
    }

    if (value.equalsIgnoreCase("null")) {
      return null;
    }

    if (value.equalsIgnoreCase("0")) {
      return 0L; // Long, to match int64 from Go
    }

    // Try parsing as a Long if the string does not start with zero
    // and is not one of the special cases above.
    if (!value.isEmpty() && value.charAt(0) != '0') {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        // Not a Long, return the string itself
      }
    }

    return value;
  }

  /** Exception indicating that the expected list structure was not found. */
  class NotAListException extends RuntimeException {
    public NotAListException(String message) {
      super(message);
    }
  }
}
