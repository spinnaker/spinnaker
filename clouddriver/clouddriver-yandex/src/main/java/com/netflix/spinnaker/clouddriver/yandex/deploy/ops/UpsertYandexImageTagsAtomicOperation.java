/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.ops;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.UpsertYandexImageTagsDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudImage;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpsertYandexImageTagsAtomicOperation
    extends AbstractYandexAtomicOperation<UpsertYandexImageTagsDescription>
    implements AtomicOperation<Void> {

  private static final String BASE_PHASE = YandexCloudFacade.UPSERT_IMAGE_TAGS;

  public UpsertYandexImageTagsAtomicOperation(UpsertYandexImageTagsDescription description) {
    super(description);
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String name = description.getImageName();
    status(BASE_PHASE, "Initializing upsert of image tags for '%s'...", name);
    YandexCloudImage image = yandexCloudFacade.getImage(credentials, name);
    if (image != null) {
      Map<String, String> labels = new HashMap<>(image.getLabels());
      labels.putAll(description.getTags());
      status(
          BASE_PHASE,
          "Upserting new labels %s in place of original labels %s for image '%s' ...",
          labels,
          image.getLabels(),
          name);
      yandexCloudFacade.updateImageTags(credentials, image.getId(), labels);
    }
    status(BASE_PHASE, "Done tagging image '%s'.", name);
    return null;
  }
}
