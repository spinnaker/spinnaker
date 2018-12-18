/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers.admin;

import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/tags")
public class EntityTagsAdminController {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final EntityTagsProvider entityTagsProvider;

  @Autowired
  public EntityTagsAdminController(Optional<EntityTagsProvider> entityTagsProvider) {
    this.entityTagsProvider = entityTagsProvider.orElse(null);
  }

  @RequestMapping(value = "/reindex", method = RequestMethod.POST)
  void reindex() {
    entityTagsProvider.reindex();
  }

  @RequestMapping(value = "/delta", method = RequestMethod.GET)
  Map delta() {
    return entityTagsProvider.delta();
  }

  @RequestMapping(value = "/reconcile", method = RequestMethod.POST)
  Map reconcile(@RequestParam(name = "dryRun", defaultValue = "true") Boolean dryRun,
                @RequestParam(name = "cloudProvider") String cloudProvider,
                @RequestParam(name = "account", required = false) String account,
                @RequestParam(name = "region", required = false) String region) {
    return entityTagsProvider.reconcile(cloudProvider, account, region, Optional.ofNullable(dryRun).orElse(true));
  }

  @RequestMapping(value = "/deleteByNamespace/{namespace}", method = RequestMethod.POST)
  Map deleteByNamespace(@PathVariable("namespace") String namespace,
                        @RequestParam(name = "dryRun", defaultValue = "true") Boolean dryRun,
                        @RequestParam(name = "deleteFromSource", defaultValue = "false") Boolean deleteFromSource) {
    return entityTagsProvider.deleteByNamespace(
      namespace,
      Optional.ofNullable(dryRun).orElse(true),
      Optional.ofNullable(deleteFromSource).orElse(false)
    );
  }
}
