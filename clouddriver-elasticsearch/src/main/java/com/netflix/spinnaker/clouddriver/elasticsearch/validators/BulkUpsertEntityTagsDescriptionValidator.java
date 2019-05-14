/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.BulkUpsertEntityTagsDescription;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component("bulkUpsertEntityTagsDescriptionValidator")
public class BulkUpsertEntityTagsDescriptionValidator
    extends DescriptionValidator<BulkUpsertEntityTagsDescription> {

  @Value("${entity-tags.max-concurrent-bulk-tags:1000}")
  Integer maxConcurrentBulkTags;

  @Override
  public void validate(
      List priorDescriptions, BulkUpsertEntityTagsDescription description, Errors errors) {
    if (description.entityTags != null && description.entityTags.size() > maxConcurrentBulkTags) {
      errors.rejectValue(
          "entityTags.length",
          "Max number of entity tags that can be submitted at once is " + maxConcurrentBulkTags);
    }
  }
}
