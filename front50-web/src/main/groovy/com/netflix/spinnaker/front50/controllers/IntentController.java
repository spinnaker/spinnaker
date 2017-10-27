/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.exceptions.DuplicateEntityException;
import com.netflix.spinnaker.front50.exceptions.InvalidRequestException;
import com.netflix.spinnaker.front50.model.intent.Intent;
import com.netflix.spinnaker.front50.model.intent.IntentDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("intents")
public class IntentController {

  @Autowired
  IntentDAO intentDAO;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "", method = RequestMethod.GET)
  List<Intent> list(@RequestParam(required = false, value = "status") List<String> status) {
    return (List<Intent>) intentDAO.getIntentsByStatus(status);
  }

  @RequestMapping(value = "{id}", method = RequestMethod.GET)
  Intent get(@PathVariable String id) {
    return intentDAO.findById(id);
  }

  @RequestMapping(value = "", method = RequestMethod.POST)
  void save(@RequestBody Intent intent) {
    checkForDuplicateIntents(intent.getId());
    intentDAO.create(intent.getId(), intent);
  }

  @RequestMapping(value = "{id}", method = RequestMethod.PUT)
  Intent update(@PathVariable String id, @RequestBody Intent intent) {
    Intent existingIntent = intentDAO.findById(id);
    if (!intent.getId().equals(existingIntent.getId())) {
      throw new InvalidRequestException("The provided id " + id + " doesn't match the intent id " + intent.getId());
    }

    intent.setLastModified(System.currentTimeMillis());
    intentDAO.update(id, intent);

    return intent;
  }

  @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
  void delete(@PathVariable String id) {
    intentDAO.delete(id);
  }

  private void checkForDuplicateIntents(String id) {
    try {
      intentDAO.findById(id);
    } catch (NotFoundException e) {
      return;
    }
    throw new DuplicateEntityException("An intent with the id " + id + " already exists");
  }

}
