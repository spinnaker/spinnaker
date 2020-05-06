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
package com.netflix.spinnaker.igor.plugins.front50;

import java.util.List;

public class PluginInfo {

  final String id;
  final List<Release> releases;

  public PluginInfo(String id, List<Release> releases) {
    this.id = id;
    this.releases = releases;
  }

  static class Release {
    final String version;
    final String date;
    final String requires;
    final String url;
    final boolean preferred;
    final String lastModified;

    public Release(
        String version,
        String date,
        String requires,
        String url,
        boolean preferred,
        String lastModified) {
      this.version = version;
      this.date = date;
      this.requires = requires;
      this.url = url;
      this.preferred = preferred;
      this.lastModified = lastModified;
    }
  }
}
