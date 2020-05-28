/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.model.AdminOperations;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Api(value = "admin", description = "Various administrative operations")
public class AdminController {

  private final Collection<AdminOperations> adminOperations;

  @Autowired
  public AdminController(Collection<AdminOperations> adminOperations) {
    this.adminOperations = adminOperations;
  }

  @ApiOperation(value = "", notes = "Recover a previously deleted object")
  @RequestMapping(value = "/recover", method = RequestMethod.POST)
  void recover(@RequestBody AdminOperations.Recover operation) {
    adminOperations.forEach(o -> o.recover(operation));
  }
}
