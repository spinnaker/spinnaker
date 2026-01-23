/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.kork.jackson;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import lombok.experimental.UtilityClass;

/**
 * Provides a utility to translate common Jackson exceptions into user-friendly error messages that
 * can help in understanding and resolving serialization or deserialization issues.
 */
@UtilityClass
public class UserFriendlyErrorHandler {

  public static String translateJacksonError(Throwable e) {
    if (e instanceof JsonParseException) {
      return handleJsonParseError((JsonParseException) e);
    } else if (e instanceof JsonMappingException) {
      return handleJsonMappingError((JsonMappingException) e);
    }
    return "Oops! Something went wrong while processing your request. Please double-check your input and try again.";
  }

  private static String handleJsonParseError(JsonParseException e) {
    // Handle basic JSON syntax errors
    if (e.getMessage().contains("Unexpected character")) {
      return "It looks like there is an unexpected character in your input. Please review it for any typos or special characters.";
    }
    if (e.getMessage().contains("Unexpected end-of-input")) {
      return "It seems your input is incomplete. Please ensure all necessary information is provided.";
    }
    return "The format of your input seems incorrect. Please make sure it follows the correct structure.";
  }

  private static String handleJsonMappingError(JsonMappingException e) {
    // Get the full path that caused the error
    StringBuilder pathBuilder = new StringBuilder();
    for (Reference ref : e.getPath()) {
      if (ref.getFieldName() != null) {
        if (pathBuilder.length() > 0 && pathBuilder.charAt(pathBuilder.length() - 1) != '.') {
          pathBuilder.append(".");
        }
        pathBuilder.append(ref.getFieldName());
      } else if (ref.getIndex() != -1) {
        pathBuilder.append("[").append(ref.getIndex()).append("]");
      }
    }
    String path = pathBuilder.length() > 0 ? pathBuilder.toString() : "unknown location";

    if (e instanceof UnrecognizedPropertyException) {
      UnrecognizedPropertyException upe = (UnrecognizedPropertyException) e;
      String knownProperties =
          String.join(
              ", ",
              upe.getKnownPropertyIds().stream().map(Object::toString).toArray(String[]::new));
      return String.format(
          "The field '%s' is not recognized at '%s'. Please remove it or correct it. Known fields are: %s.",
          upe.getPropertyName(), path, knownProperties);
    }

    if (e instanceof InvalidFormatException) {
      InvalidFormatException ife = (InvalidFormatException) e;
      String expectedType = ife.getTargetType().getSimpleName();
      String actualValue = ife.getValue().toString();
      return String.format(
          "The value '%s' for the field at '%s' is not valid. It should be a %s. Please correct it and try again.",
          actualValue, path, simplifyTypeName(expectedType));
    }

    if (e instanceof MismatchedInputException) {
      String targetType =
          ((MismatchedInputException) e).getTargetType() != null
              ? ((MismatchedInputException) e).getTargetType().getSimpleName()
              : "unknown type";
      return String.format(
          "The input provided at '%s' does not match the expected format for '%s'. Please check the documentation and provide the correct format.",
          path, targetType);
    }

    if (e.getMessage().contains("no String-argument constructor")) {
      return String.format(
          "The input format is incorrect at '%s'. It looks like we cannot create the target object from a single value. Please provide the data in the correct structure.",
          path);
    }

    return String.format(
        "There seems to be an issue with the field at '%s'. Please check its value and format.",
        path);
  }

  private static String simplifyTypeName(String typeName) {
    switch (typeName.toLowerCase()) {
      case "integer":
        return "whole number";
      case "double":
      case "float":
        return "decimal number";
      case "boolean":
        return "true/false value";
      case "localdate":
        return "date";
      case "localdatetime":
        return "date and time";
      default:
        return typeName.toLowerCase();
    }
  }
}
