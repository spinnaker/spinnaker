/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.cats.redis.cache;

import com.google.common.base.Preconditions;

public class RedisCacheOptions {
    public static Builder builder() {
        return new Builder();
    }
    private final int maxMsetSize;
    private final int maxMgetSize;
    private final int maxHmgetSize;
    private final int maxHmsetSize;
    private final int maxSaddSize;
    private final int maxDelSize;
    private final int maxPipelineSize;
    private final int scanSize;
    private final int maxMergeBatchSize;
    private final int maxEvictBatchSize;
    private final int maxGetBatchSize;
    private final boolean hashingEnabled;
    private final boolean treatRelationshipsAsSet;

    private static int posInt(String name, int value) {
        Preconditions.checkArgument(value > 0, "%s must be a positive integer (%s)", name, value);
        return value;
    }

    private static int posEven(String name, int value) {
        Preconditions.checkArgument(value > 0 && value % 2 == 0, "%s must be a positive even integer (%s)", name, value);
        return value;
    }

    public RedisCacheOptions(int maxMsetSize,
                             int maxMgetSize,
                             int maxHmgetSize,
                             int maxHmsetSize,
                             int maxSaddSize,
                             int maxDelSize,
                             int maxPipelineSize,
                             int scanSize,
                             int maxMergeBatchSize,
                             int maxEvictBatchSize,
                             int maxGetBatchSize,
                             boolean hashingEnabled,
                             boolean treatRelationshipsAsSet) {
        this.maxMsetSize = posEven("maxMsetSize", maxMsetSize);
        this.maxMgetSize = posInt("maxMgetSize", maxMgetSize);
        this.maxHmgetSize = posInt("maxHmgetSize", maxHmgetSize);
        this.maxHmsetSize = posInt("maxHmsetSize", maxHmsetSize);
        this.maxSaddSize = posInt("maxSaddSize", maxSaddSize);
        this.maxDelSize = posInt("maxDelSize", maxDelSize);
        this.maxPipelineSize = posInt("maxPipelineSize", maxPipelineSize);
        this.scanSize = posInt("scanSize", scanSize);
        this.maxMergeBatchSize = posInt("maxMergeBatchSize", maxMergeBatchSize);
        this.maxEvictBatchSize = posInt("maxEvictBatchSize", maxEvictBatchSize);
        this.maxGetBatchSize = posInt("maxGetBatchSize", maxGetBatchSize);
        this.hashingEnabled = hashingEnabled;
        this.treatRelationshipsAsSet = treatRelationshipsAsSet;
    }

    public int getMaxMsetSize() {
        return maxMsetSize;
    }

    public int getMaxMgetSize() {
        return maxMgetSize;
    }

    public int getMaxHmgetSize() {
        return maxHmgetSize;
    }

    public int getMaxHmsetSize() {
        return maxHmsetSize;
    }

    public int getMaxSaddSize() {
        return maxSaddSize;
    }

    public int getMaxDelSize() {
        return maxDelSize;
    }

    public int getMaxPipelineSize() {
        return maxPipelineSize;
    }

    public int getScanSize() {
        return scanSize;
    }

    public int getMaxMergeBatchSize() {
        return maxMergeBatchSize;
    }

    public int getMaxEvictBatchSize() {
        return maxEvictBatchSize;
    }

    public int getMaxGetBatchSize() {
        return maxGetBatchSize;
    }

    public boolean isHashingEnabled() {
        return hashingEnabled;
    }

    public boolean isTreatRelationshipsAsSet() {
        return treatRelationshipsAsSet;
    }

  public static class Builder {
        public static final int DEFAULT_MULTI_OP_SIZE = 200;
        public static final int DEFAULT_BATCH_SIZE = 200;
        public static final int DEFAULT_SCAN_SIZE = 200;
        public static final int DEFAULT_MAX_PIPELINE_SIZE = 200;
        public static final boolean DEFAULT_HASHING_ENABLED = true;
        public static final boolean DEFAULT_TREAT_RELATIONSHIPS_AS_SET_DISABLED = false;

        int maxMsetSize;
        int maxMgetSize;
        int maxHmgetSize;
        int maxHmsetSize;
        int maxSaddSize;
        int maxDelSize;
        int maxPipelineSize;
        int scanSize;
        int maxMergeBatchSize;
        int maxEvictBatchSize;
        int maxGetBatchSize;
        boolean hashingEnabled;
        boolean treatRelationshipsAsSet;

        public Builder() {
            batchSize(DEFAULT_BATCH_SIZE);
            scan(DEFAULT_SCAN_SIZE);
            multiOp(DEFAULT_MULTI_OP_SIZE);
            maxPipeline(DEFAULT_MAX_PIPELINE_SIZE);
            hashing(DEFAULT_HASHING_ENABLED);
            treatRelationshipsAsSet(DEFAULT_TREAT_RELATIONSHIPS_AS_SET_DISABLED);
        }

        public Builder maxMergeBatch(int maxMergeBatch) {
            this.maxMergeBatchSize = maxMergeBatch;
            return this;
        }

        public Builder maxEvictBatch(int maxEvictBatch) {
            this.maxEvictBatchSize = maxEvictBatch;
            return this;
        }

        public Builder maxGetBatch(int maxGetBatch) {
            this.maxGetBatchSize = maxGetBatch;
            return this;
        }

        public Builder batchSize(int batchSize) {
            return maxMergeBatch(batchSize).maxEvictBatch(batchSize).maxGetBatch(batchSize);
        }

        public Builder scan(int scanSize) {
            this.scanSize = scanSize;
            return this;
        }

        public Builder multiOp(int multiOpSize) {
            return maxMkeyOp(multiOpSize).maxHmOp(multiOpSize).maxSadd(multiOpSize).maxDel(multiOpSize);
        }

        public Builder maxMset(int maxMset) {
            this.maxMsetSize = maxMset;
            return this;
        }

        public Builder maxMget(int maxMget) {
            this.maxMgetSize = maxMget;
            return this;
        }

        public Builder maxMkeyOp(int maxMkey) {
            return maxMset(maxMkey).maxMget(maxMkey);
        }

        public Builder maxHmget(int maxHmget) {
            this.maxHmgetSize = maxHmget;
            return this;
        }

        public Builder maxHmset(int maxHmset) {
            this.maxHmsetSize = maxHmset;
            return this;
        }

        public Builder maxHmOp(int maxHmOp) {
            return maxHmget(maxHmOp).maxHmset(maxHmOp);
        }

        public Builder maxSadd(int maxSadd) {
            this.maxSaddSize = maxSadd;
            return this;
        }

        public Builder maxDel(int maxDel) {
            this.maxDelSize = maxDel;
            return this;
        }

        public Builder maxPipeline(int maxPipeline) {
            this.maxPipelineSize = maxPipeline;
            return this;
        }

        public Builder hashing(boolean hashingEnabled) {
            this.hashingEnabled = hashingEnabled;
            return this;
        }

        public Builder treatRelationshipsAsSet(boolean treatRelationshipsAsSet) {
            this.treatRelationshipsAsSet = treatRelationshipsAsSet;
            return this;
        }

        public RedisCacheOptions build() {
            return new RedisCacheOptions(
              maxMsetSize,
              maxMgetSize,
              maxHmgetSize,
              maxHmsetSize,
              maxSaddSize,
              maxDelSize,
              maxPipelineSize,
              scanSize,
              maxMergeBatchSize,
              maxEvictBatchSize,
              maxGetBatchSize,
              hashingEnabled,
              treatRelationshipsAsSet);
        }

        public void setBatchSize(int batchSize) {
            batchSize(batchSize);
        }

        public void setMultiOpSize(int multiOpSize) {
            multiOp(multiOpSize);
        }

        public int getMaxMsetSize() {
            return maxMsetSize;
        }

        public void setMaxMsetSize(int maxMsetSize) {
            this.maxMsetSize = maxMsetSize;
        }

        public int getMaxMgetSize() {
            return maxMgetSize;
        }

        public void setMaxMgetSize(int maxMgetSize) {
            this.maxMgetSize = maxMgetSize;
        }

        public int getMaxHmgetSize() {
            return maxHmgetSize;
        }

        public void setMaxHmgetSize(int maxHmgetSize) {
            this.maxHmgetSize = maxHmgetSize;
        }

        public int getMaxHmsetSize() {
            return maxHmsetSize;
        }

        public void setMaxHmsetSize(int maxHmsetSize) {
            this.maxHmsetSize = maxHmsetSize;
        }

        public int getMaxSaddSize() {
            return maxSaddSize;
        }

        public void setMaxSaddSize(int maxSaddSize) {
            this.maxSaddSize = maxSaddSize;
        }

        public int getMaxDelSize() {
            return maxDelSize;
        }

        public void setMaxDelSize(int maxDelSize) {
            this.maxDelSize = maxDelSize;
        }

        public int getMaxPipelineSize() {
            return maxPipelineSize;
        }

        public void setMaxPipelineSize(int maxPipelineSize) {
            this.maxPipelineSize = maxPipelineSize;
        }

        public int getScanSize() {
            return scanSize;
        }

        public void setScanSize(int scanSize) {
            this.scanSize = scanSize;
        }

        public int getMaxMergeBatchSize() {
            return maxMergeBatchSize;
        }

        public void setMaxMergeBatchSize(int maxMergeBatchSize) {
            this.maxMergeBatchSize = maxMergeBatchSize;
        }

        public int getMaxEvictBatchSize() {
            return maxEvictBatchSize;
        }

        public void setMaxEvictBatchSize(int maxEvictBatchSize) {
            this.maxEvictBatchSize = maxEvictBatchSize;
        }

        public int getMaxGetBatchSize() {
            return maxGetBatchSize;
        }

        public void setMaxGetBatchSize(int maxGetBatchSize) {
            this.maxGetBatchSize = maxGetBatchSize;
        }

        public boolean isHashingEnabled() {
            return hashingEnabled;
        }

        public void setHashingEnabled(boolean hashingEnabled) {
            this.hashingEnabled = hashingEnabled;
        }

        public boolean isTreatRelationshipsAsSet() {
            return treatRelationshipsAsSet;
        }

        public void setTreatRelationshipsAsSet(boolean treatRelationshipsAsSet) {
            this.treatRelationshipsAsSet = treatRelationshipsAsSet;
        }
  }
}
