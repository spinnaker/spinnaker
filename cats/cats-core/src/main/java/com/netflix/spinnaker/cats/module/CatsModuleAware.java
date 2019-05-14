/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.cats.module;

/**
 * This class is used to identify classes (typically Schedulers) that are capable of returning the
 * cats module they are associated with.
 */
public abstract class CatsModuleAware {
  private CatsModule catsModule;

  /** Set this object's cats module. */
  public void setCatsModule(CatsModule catsModule) {
    this.catsModule = catsModule;
  }

  /** Get this object's cats module. */
  public CatsModule getCatsModule() {
    return catsModule;
  }
}
