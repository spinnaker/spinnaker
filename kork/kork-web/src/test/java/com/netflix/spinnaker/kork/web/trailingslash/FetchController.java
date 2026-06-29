/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.web.trailingslash;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller mapped to {@code /fetch} (no trailing slash), mirroring the real {@code
 * ArtifactController.fetch} mapping that Orca calls with a trailing slash.
 *
 * <p>Lives in an isolated package so it is not picked up by other tests' component scans. It is
 * registered explicitly via {@code @SpringBootTest(classes = ...)} rather than component scanning.
 */
@RestController
public class FetchController {

  @PutMapping("/fetch")
  String fetch(@RequestBody String body) {
    return "fetched:" + body;
  }

  /**
   * Endpoint intentionally mapped WITH a trailing slash. When {@code UrlHandlerFilter} is enabled
   * it trims the trailing slash before dispatch, so requests to {@code /fetchWithSlash/} are
   * dispatched as {@code /fetchWithSlash} and never match this mapping (they 404). The distinct
   * return value lets tests confirm whether this method is ever reached.
   */
  @PutMapping("/fetchWithSlash/")
  String fetchWithSlash(@RequestBody String body) {
    return "fetchedWithSlash:" + body;
  }

  /**
   * Root mapping, mirroring controllers such as echo's {@code HistoryController} and gate's {@code
   * RootController} that map {@code "/"}. Used to verify the filter does not trim the root path to
   * an empty string and break it.
   */
  @PostMapping("/")
  String root(@RequestBody String body) {
    return "root:" + body;
  }
}
