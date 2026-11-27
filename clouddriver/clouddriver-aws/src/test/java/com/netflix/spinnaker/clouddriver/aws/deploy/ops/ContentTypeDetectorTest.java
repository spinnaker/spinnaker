/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContentTypeDetectorTest {

  @Test
  void shouldDetectContentTypeForKnownExtensions() {
    assertEquals("text/html", ContentTypeDetector.detectContentType("index.html"));
    assertEquals("text/plain", ContentTypeDetector.detectContentType("readme.txt"));
    assertEquals("image/png", ContentTypeDetector.detectContentType("logo.png"));
    assertEquals("image/jpeg", ContentTypeDetector.detectContentType("photo.jpg"));
    assertEquals("application/pdf", ContentTypeDetector.detectContentType("doc.pdf"));
    assertEquals("text/javascript", ContentTypeDetector.detectContentType("script.js"));
    assertEquals("font/woff2", ContentTypeDetector.detectContentType("font.woff2"));
    assertEquals("images/x-icon", ContentTypeDetector.detectContentType("favicon.ico"));
    assertEquals("text/css", ContentTypeDetector.detectContentType("style.css"));
  }

  @Test
  void shouldFallbackToOctetStreamForUnknownExtensions() {
    assertEquals("application/octet-stream", ContentTypeDetector.detectContentType("data.xyz123"));
    assertEquals("application/octet-stream", ContentTypeDetector.detectContentType("noextension"));
  }

  @Test
  void shouldHandleNullFilename() {
    assertEquals("application/octet-stream", ContentTypeDetector.detectContentType(null));
  }

  @Test
  void shouldHandleEmptyFilename() {
    assertEquals("application/octet-stream", ContentTypeDetector.detectContentType(""));
  }

  @Test
  void shouldHandleFilenameWithoutExtension() {
    assertEquals("application/octet-stream", ContentTypeDetector.detectContentType("README"));
  }

  @Test
  void shouldHandleFilenameEndingWithDot() {
    assertEquals("application/octet-stream", ContentTypeDetector.detectContentType("file."));
  }

  @Test
  void shouldBeCaseInsensitive() {
    assertEquals("text/html", ContentTypeDetector.detectContentType("INDEX.HTML"));
    assertEquals("text/html", ContentTypeDetector.detectContentType("page.HTml"));
    assertEquals("image/png", ContentTypeDetector.detectContentType("logo.PNG"));
  }

  @Test
  void shouldHandleMultipleDotsInFilename() {
    assertEquals("text/javascript", ContentTypeDetector.detectContentType("app.min.js"));
    assertEquals("text/css", ContentTypeDetector.detectContentType("style.bundle.min.css"));
  }
}
