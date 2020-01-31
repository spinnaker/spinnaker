/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class ExecutionsController {

  private OrcaServiceSelector orcaServiceSelector;

  @Autowired
  public ExecutionsController(OrcaServiceSelector orcaServiceSelector) {
    this.orcaServiceSelector = orcaServiceSelector;
  }

  @ApiOperation(
      value =
          "Retrieves an ad-hoc collection of executions based on a number of user-supplied parameters. Either executionIds or pipelineConfigIds must be supplied in order to return any results. If both are supplied, an exception will be thrown.")
  @RequestMapping(value = "/executions", method = RequestMethod.GET)
  List getLatestExecutionsByConfigIds(
      @ApiParam(
              value =
                  "A comma-separated list of pipeline configuration IDs to retrieve recent executions for. Either this OR pipelineConfigIds must be supplied, but not both.")
          @RequestParam(value = "pipelineConfigIds", required = false)
          String pipelineConfigIds,
      @ApiParam(
              value =
                  "A comma-separated list of executions to retrieve. Either this OR pipelineConfigIds must be supplied, but not both.")
          @RequestParam(value = "executionIds", required = false)
          String executionIds,
      @ApiParam(
              value =
                  "The number of executions to return per pipeline configuration. Ignored if executionIds parameter is supplied. If this value is missing, it is defaulted to 1.")
          @RequestParam(value = "limit", required = false)
          Integer limit,
      @ApiParam(
              value =
                  "A comma-separated list of execution statuses to filter by. Ignored if executionIds parameter is supplied. If this value is missing, it is defaulted to all statuses.")
          @RequestParam(value = "statuses", required = false)
          String statuses,
      @ApiParam(
              value =
                  "Expands each execution object in the resulting list. If this value is missing, it is defaulted to true.")
          @RequestParam(value = "expand", defaultValue = "true")
          boolean expand) {
    if ((executionIds == null || executionIds.trim().isEmpty())
        && (pipelineConfigIds == null || pipelineConfigIds.trim().isEmpty())) {
      return Collections.emptyList();
    }

    return orcaServiceSelector
        .select()
        .getSubsetOfExecutions(pipelineConfigIds, executionIds, limit, statuses, expand);
  }

  @ApiOperation(
      value =
          "Search for pipeline executions using a combination of criteria. The returned list is sorted by buildTime (trigger time) in reverse order so that newer executions are first in the list.")
  @RequestMapping(
      value = "/applications/{application}/executions/search",
      method = RequestMethod.GET)
  List searchForPipelineExecutionsByTrigger(
      @ApiParam(
              value =
                  "Only includes executions that are part of this application. If this value is \"*\", results will include executions of all applications.",
              required = true)
          @PathVariable(value = "application")
          String application,
      @ApiParam(
              value =
                  "Only includes executions that were triggered by a trigger with a type that is equal to a type provided in this field. The list of trigger types should be a comma-delimited string. If this value is missing, results will includes executions of all trigger types.")
          @RequestParam(value = "triggerTypes", required = false)
          String triggerTypes,
      @ApiParam(value = "Only includes executions that with this pipeline name.")
          @RequestParam(value = "pipelineName", required = false)
          String pipelineName,
      @ApiParam(
              value =
                  "Only includes executions that were triggered by a trigger with this eventId.")
          @RequestParam(value = "eventId", required = false)
          String eventId,
      @ApiParam(
              value =
                  "Only includes executions that were triggered by a trigger that matches the subset of fields provided by this value. This value should be a base64-encoded string of a JSON representation of a trigger object. The comparison succeeds if the execution trigger contains all the fields of the input trigger, the fields are of the same type, and each value of the field \"matches\". The term \"matches\" is specific for each field's type:\n"
                      + "- For Strings: A String value in the execution's trigger matches the input trigger's String value if the former equals the latter (case-insensitive) OR if the former matches the latter as a regular expression.\n"
                      + "- For Maps: A Map value in the execution's trigger matches the input trigger's Map value if the former contains all keys of the latter and their values match.\n"
                      + "- For Collections: A Collection value in the execution's trigger matches the input trigger's Collection value if the former has a unique element that matches each element of the latter.\n"
                      + "- Every other value is compared using the Java \"equals\" method (Groovy \"==\" operator)")
          @RequestParam(value = "trigger", required = false)
          String trigger,
      @ApiParam(
              value =
                  "Only includes executions that were built at or after the given time, represented as a Unix timestamp in ms (UTC). This value must be >= 0 and <= the value of [triggerTimeEndBoundary], if provided. If this value is missing, it is defaulted to 0.")
          @RequestParam(value = "triggerTimeStartBoundary", defaultValue = "0")
          long triggerTimeStartBoundary,
      @ApiParam(
              value =
                  "Only includes executions that were built at or before the given time, represented as a Unix timestamp in ms (UTC). This value must be <= 9223372036854775807 (Long.MAX_VALUE) and >= the value of [triggerTimeStartBoundary], if provided. If this value is missing, it is defaulted to 9223372036854775807.")
          @RequestParam(
              value = "triggerTimeEndBoundary",
              defaultValue = "9223372036854775807" /* Long.MAX_VALUE */)
          long triggerTimeEndBoundary,
      @ApiParam(
              value =
                  "Only includes executions with a status that is equal to a status provided in this field. The list of statuses should be given as a comma-delimited string. If this value is missing, includes executions of all statuses. Allowed statuses are: NOT_STARTED, RUNNING, PAUSED, SUSPENDED, SUCCEEDED, FAILED_CONTINUE, TERMINAL, CANCELED, REDIRECT, STOPPED, SKIPPED, BUFFERED.")
          @RequestParam(value = "statuses", required = false)
          String statuses,
      @ApiParam(
              value =
                  "Sets the first item of the resulting list for pagination. The list is 0-indexed. This value must be >= 0. If this value is missing, it is defaulted to 0.")
          @RequestParam(value = "startIndex", defaultValue = "0")
          int startIndex,
      @ApiParam(
              value =
                  "Sets the size of the resulting list for pagination. This value must be > 0. If this value is missing, it is defaulted to 10.")
          @RequestParam(value = "size", defaultValue = "10")
          int size,
      @ApiParam(
              value =
                  "Reverses the resulting list before it is paginated. If this value is missing, it is defaulted to false.")
          @RequestParam(value = "reverse", defaultValue = "false")
          boolean reverse,
      @ApiParam(
              value =
                  "Expands each execution object in the resulting list. If this value is missing, it is defaulted to false.")
          @RequestParam(value = "expand", defaultValue = "false")
          boolean expand) {
    return orcaServiceSelector
        .select()
        .searchForPipelineExecutionsByTrigger(
            application,
            triggerTypes,
            pipelineName,
            eventId,
            trigger,
            triggerTimeStartBoundary,
            triggerTimeEndBoundary,
            statuses,
            startIndex,
            size,
            reverse,
            expand);
  }
}
