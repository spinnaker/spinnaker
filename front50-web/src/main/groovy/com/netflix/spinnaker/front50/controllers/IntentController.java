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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@ConditionalOnBean(IntentDAO.class)
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
    return intentDAO.findById(id.toLowerCase());
  }

  @RequestMapping(value = "", method = RequestMethod.POST)
  Intent upsert(@RequestBody Intent intent) {
    intent.setLastModified(System.currentTimeMillis());

    if (intentExists(intent.getId())){
      intentDAO.update(intent.getId(), intent);
    } else {
      intentDAO.create(intent.getId(), intent);
    }

    return intentDAO.findById(intent.getId());
  }

  @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
  void delete(@PathVariable String id) {
    intentDAO.delete(id.toLowerCase());
  }

  private void checkForDuplicateIntents(String id) {
    try {
      intentDAO.findById(id.toLowerCase());
    } catch (NotFoundException e) {
      return;
    }
    throw new DuplicateEntityException("An intent with the id " + id + " already exists");
  }

  private boolean intentExists(String id) {
    try {
      intentDAO.findById(id.toLowerCase());
    } catch (NotFoundException e) {
      return false;
    }
    return true;
  }
}
