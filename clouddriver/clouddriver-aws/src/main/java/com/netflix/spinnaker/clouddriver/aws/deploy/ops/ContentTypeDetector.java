/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import org.springframework.boot.web.server.MimeMappings;

/**
 * Utility class for detecting content types based on file extensions.
 *
 * <p>Provides custom MIME type mappings that extend Spring Boot's default mappings with additional
 * overrides for specific file types (.ico, .woff2) that may not be correctly mapped.
 */
public final class ContentTypeDetector {

  private static final MimeMappings MIME_MAPPINGS;
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  static {
    // start with default mappings
    MIME_MAPPINGS = new MimeMappings(MimeMappings.DEFAULT);
    MIME_MAPPINGS.add("html", "text/html");
    MIME_MAPPINGS.add("htm", "text/html");
    MIME_MAPPINGS.add("css", "text/css");
    MIME_MAPPINGS.add("txt", "text/plain");
    MIME_MAPPINGS.add("png", "image/png");
    MIME_MAPPINGS.add("jpg", "image/jpeg");
    MIME_MAPPINGS.add("jpeg", "image/jpeg");
    MIME_MAPPINGS.add("pdf", "application/pdf");

    // custom overrides for specific file types
    MIME_MAPPINGS.add(
        "js",
        "text/javascript"); // https://github.com/spring-projects/spring-framework/issues/33197, can
    // remove on Spring Boot>=3.4
    MIME_MAPPINGS.add("ico", "images/x-icon");
    MIME_MAPPINGS.add("woff2", "font/woff2");
  }

  private ContentTypeDetector() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Detects the content type for a file based on its extension.
   *
   * @param filename the filename to detect content type for
   * @return the detected MIME type, or "application/octet-stream" if unknown
   */
  public static String detectContentType(String filename) {
    if (filename == null || filename.isEmpty()) {
      return DEFAULT_CONTENT_TYPE;
    }

    // Extract extension
    int lastDot = filename.lastIndexOf('.');
    if (lastDot == -1 || lastDot == filename.length() - 1) {
      return DEFAULT_CONTENT_TYPE;
    }

    String extension = filename.substring(lastDot + 1).toLowerCase();
    String contentType = MIME_MAPPINGS.get(extension);

    return contentType != null ? contentType : DEFAULT_CONTENT_TYPE;
  }
}
