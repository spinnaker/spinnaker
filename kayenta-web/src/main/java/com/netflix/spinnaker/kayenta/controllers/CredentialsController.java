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

package com.netflix.spinnaker.kayenta.controllers;

import com.netflix.spinnaker.kayenta.security.AccountCredentials;
import com.netflix.spinnaker.kayenta.security.AccountCredentialsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/credentials")
public class CredentialsController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @RequestMapping(method = RequestMethod.GET)
  Set<? extends AccountCredentials> list() {
    return accountCredentialsRepository.getAll();
  }
}