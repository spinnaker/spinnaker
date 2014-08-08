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



package com.netflix.spinnaker.front50.model.application

import com.netflix.spinnaker.front50.exception.NotFoundException

public interface ApplicationDAO {
  Application findByName(String name) throws NotFoundException

  Set<Application> all() throws NotFoundException

  Application create(String id, Map<String, String> attributes)

  void update(String id, Map<String, String> attributes)

  void delete(String id)

  boolean isHealthly()

}