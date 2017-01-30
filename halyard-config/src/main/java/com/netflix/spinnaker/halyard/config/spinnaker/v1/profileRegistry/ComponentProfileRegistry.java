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

package com.netflix.spinnaker.halyard.config.spinnaker.v1.profileRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ComponentProfileRegistry {
  @Autowired
  OkClient okClient;

  @Autowired
  ComponentProfileRegistryService componentProfileRegistryService;

  @Autowired
  String spinconfigBucket;

  @Bean
  public ComponentProfileRegistryService componentProfileRegistryService() {
    return new RestAdapter.Builder()
        .setClient(okClient)
        .setEndpoint("https://www.googleapis.com")
        .build()
        .create(ComponentProfileRegistryService.class);
  }

  public InputStream getObjectContents(String objectName) throws IOException {
    ComponentProfileRegistryService service = componentProfileRegistryService;

    StoredObjectMetadata metadata = service.getMetadata(spinconfigBucket, objectName);

    return service.getContents(spinconfigBucket, objectName, metadata.getGeneration(), "media").getBody().in();
  }
}
