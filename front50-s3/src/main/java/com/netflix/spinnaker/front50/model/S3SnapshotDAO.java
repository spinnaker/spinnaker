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

package com.netflix.spinnaker.front50.model;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.model.snapshot.Snapshot;
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO;
import org.springframework.util.Assert;
import rx.Scheduler;

import java.util.Collection;

public class S3SnapshotDAO extends S3Support<Snapshot> implements SnapshotDAO {

    public S3SnapshotDAO(ObjectMapper objectMapper,
                         AmazonS3 amazonS3,
                         Scheduler scheduler,
                         int refreshIntervalMs,
                         String bucket,
                         String rootFolder) {
        super(objectMapper, amazonS3, scheduler, refreshIntervalMs, bucket, (rootFolder + "/snapshots/").replaceAll("//", "/"));
    }

    @Override
    public Collection<Snapshot> getHistory(String id, int maxResults) {
        return allVersionsOf(id, maxResults);
    }


    @Override
    public void update(String id, Snapshot item) {
        if (item.getId() == null) {
            item.setId(id);
        }
        item.setTimestamp(System.currentTimeMillis());
        super.update(id, item);
    }

    @Override
    public Snapshot create(String id, Snapshot item) {
        Assert.notNull(item.getApplication(), "application field must NOT be null!");
        Assert.notNull(item.getAccount(), "account field must NOT be null!");
        if (id == null) {
            id = item.getApplication() + "-" + item.getAccount();
        }
        item.setId(id);
        item.setTimestamp(System.currentTimeMillis());

        super.update(id, item);
        return findById(id);
    }

    @Override
    public String getMetadataFilename() {
        return "snapshot.json";
    }

    @Override
    Class<Snapshot> getSerializedClass() {
        return Snapshot.class;
    }
}
