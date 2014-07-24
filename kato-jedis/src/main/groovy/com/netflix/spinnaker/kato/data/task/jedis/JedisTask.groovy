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

package com.netflix.spinnaker.kato.data.task.jedis

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.kato.data.task.DefaultTask

class JedisTask extends DefaultTask {

  @JsonIgnore
  JedisTaskRepository repository

  JedisTask(String id, String phase, String status, JedisTaskRepository repository = null) {
    super(id)
    this.repository = repository
    updateStatus(phase, status)
  }

  JedisTask(String id, long startTimeMs, Boolean complete, Boolean failed) {
    super(id)
    if (failed) {
      this.fail()
    }
    if (complete && !failed) {
      this.complete()
    }
    this.startTimeMs = startTimeMs
    this.id = id
  }

  @Override
  void updateStatus(String phase, String status) {
    super.updateStatus(phase, status)
    repository?.addToHistory(phase, status, this)
    save()
  }

  @Override
  void complete() {
    super.complete()
    save()
  }

  @Override
  public void addResultObjects(List<Object> results) {
    if (!repository) {
      throw new IllegalStateException("No repository found!")
    }
    results.each {
      repository.addResultObject(it, this)
    }
  }

  public List<Object> getResultObjects() {
    repository.getResultObjects(this)
  }

  public List<Object> getHistory() {
    repository.getHistory(this)
  }

  private void save() {
    repository?.set(this.id, this)
  }

}
