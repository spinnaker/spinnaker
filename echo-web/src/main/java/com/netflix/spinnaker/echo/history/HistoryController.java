/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.history;

import com.netflix.spinnaker.echo.events.EventPropagator;
import com.netflix.spinnaker.echo.model.Event;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for history
 */
@RestController
public class HistoryController {
  private EventPropagator propagator;

  public HistoryController(EventPropagator propagator) {
    this.propagator = propagator;
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  public void saveHistory(@RequestBody Event event) {
    propagator.processEvent(event);
  }
}
