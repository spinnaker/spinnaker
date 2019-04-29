/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.controllers;

import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ControllerSupport {

  static final String ANONYMOUS_USER = "anonymous";

  static String convert(String in) {
    try {
      String decoded = URLDecoder.decode(in, "UTF-8");
      if (ANONYMOUS_USER.equalsIgnoreCase(decoded)) {
        return UnrestrictedResourceConfig.UNRESTRICTED_USERNAME;
      }
      return decoded;
    } catch (UnsupportedEncodingException uee) {
      log.error("Decoding exception for string " + in, uee);
    }
    return null;
  }
}
