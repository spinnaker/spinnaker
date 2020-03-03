/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.common;

/**
 * Known X-SPINNAKER headers, but any X-SPINNAKER-* key in the MDC will be automatically propagated
 * to the HTTP headers.
 *
 * <p>Use makeCustomerHeader() to add customer headers
 */
public enum Header {
  USER("X-SPINNAKER-USER", true),
  ACCOUNTS("X-SPINNAKER-ACCOUNTS", true),
  USER_ORIGIN("X-SPINNAKER-USER-ORIGIN", false),
  REQUEST_ID("X-SPINNAKER-REQUEST-ID", false),
  EXECUTION_ID("X-SPINNAKER-EXECUTION-ID", false),
  EXECUTION_TYPE("X-SPINNAKER-EXECUTION-TYPE", false),
  APPLICATION("X-SPINNAKER-APPLICATION", false),
  PLUGIN_ID("X-SPINNAKER-PLUGIN-ID", false),
  PLUGIN_EXTENSION("X-SPINNAKER-PLUGIN-EXTENSION", false);

  private String header;
  private boolean isRequired;

  Header(String header, boolean isRequired) {
    this.header = header;
    this.isRequired = isRequired;
  }

  public String getHeader() {
    return header;
  }

  public boolean isRequired() {
    return isRequired;
  }

  public static String XSpinnakerPrefix = "X-SPINNAKER-";
  public static String XSpinnakerAnonymous = XSpinnakerPrefix + "ANONYMOUS";

  public static String makeCustomHeader(String header) {
    return XSpinnakerPrefix + header.toUpperCase();
  }

  @Override
  public String toString() {
    return "Header{" + "header='" + header + '\'' + '}';
  }
}
