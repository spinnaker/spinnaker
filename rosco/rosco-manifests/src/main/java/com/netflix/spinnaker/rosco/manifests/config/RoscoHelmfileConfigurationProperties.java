/*
 * Copyright 2023 Grab Holdings, Inc.
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

package com.netflix.spinnaker.rosco.manifests.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("helmfile")
@Data
public class RoscoHelmfileConfigurationProperties {
  private String executablePath = "helmfile";

  /**
   * Helmfile supports `hooks:` (events such as `prepare`/`cleanup`, which run even for the
   * otherwise side-effect-free `helmfile template` command) and `postRenderers:` (also settable via
   * `helmDefaults.args`/per-release `args` containing `--post-renderer`/ `--post-renderer-args`),
   * both of which have helmfile execute an arbitrary local command/script as part of baking.
   * Because the helmfile.yaml content baked here is supplied as an input artifact - and so may not
   * be as trusted as the pipeline that references it (e.g. a git branch anyone can push to) - rosco
   * refuses to bake helmfile content that declares either feature unless this is explicitly set to
   * true. Only enable this for helmfile sources you trust as much as the code they deploy.
   */
  private boolean allowHooksAndPostRenderers = false;
}
