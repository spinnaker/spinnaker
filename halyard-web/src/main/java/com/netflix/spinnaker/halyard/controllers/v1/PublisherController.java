/*
 * Copyright 2019 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Publisher;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsubs;
import com.netflix.spinnaker.halyard.config.services.v1.PublisherService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/pubsubs/{pubsubName:.+}/publishers")
public class PublisherController {
  private final PublisherService publisherService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Publisher>> publishers(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Publisher>>builder()
        .getter(() -> publisherService.getAllPublishers(deploymentName, pubsubName))
        .validator(() -> publisherService.validateAllPublishers(deploymentName, pubsubName))
        .description("Get all " + pubsubName + " publishers")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/publisher/{publisherName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Publisher> publisher(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String publisherName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Publisher>builder()
        .getter(
            () -> publisherService.getPubsubPublisher(deploymentName, pubsubName, publisherName))
        .validator(
            () -> publisherService.validatePublisher(deploymentName, pubsubName, publisherName))
        .description("Get " + publisherName + " publisher")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/publisher/{publisherName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deletePublisher(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String publisherName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> publisherService.deletePublisher(deploymentName, pubsubName, publisherName))
        .validator(() -> publisherService.validateAllPublishers(deploymentName, pubsubName))
        .description("Delete the " + publisherName + " publisher")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/publisher/{publisherName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setPublisher(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @PathVariable String publisherName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawPublisher) {
    Publisher publisher =
        objectMapper.convertValue(rawPublisher, Pubsubs.translatePublisherType(pubsubName));
    return GenericUpdateRequest.<Publisher>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(s -> publisherService.setPublisher(deploymentName, pubsubName, publisherName, s))
        .validator(
            () ->
                publisherService.validatePublisher(deploymentName, pubsubName, publisher.getName()))
        .description("Edit the " + publisherName + " publisher")
        .build()
        .execute(validationSettings, publisher);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addPublisher(
      @PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawPublisher) {
    Publisher publisher =
        objectMapper.convertValue(rawPublisher, Pubsubs.translatePublisherType(pubsubName));
    return GenericUpdateRequest.<Publisher>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(s -> publisherService.addPublisher(deploymentName, pubsubName, s))
        .validator(
            () ->
                publisherService.validatePublisher(deploymentName, pubsubName, publisher.getName()))
        .description("Add the " + publisher.getName() + " publisher")
        .build()
        .execute(validationSettings, publisher);
  }
}
