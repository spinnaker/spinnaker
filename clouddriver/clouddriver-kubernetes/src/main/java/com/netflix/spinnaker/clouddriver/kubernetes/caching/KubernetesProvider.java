/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.security.BaseProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class KubernetesProvider extends BaseProvider implements ProviderCacheConfiguration {
  public static final String PROVIDER_NAME = KubernetesCloudProvider.ID;

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  /**
   * KubernetesCachingAgent#buildCacheResult backfills an empty placeholder entry for every kind
   * it's authoritative for, so the SQL cache's existingIds-minus-currentIds eviction diff always
   * runs, even when a kind has dropped to zero live resources (e.g. the last Deployment in a
   * namespace was deleted). Without opting in here, SqlCache's default safeguard against ever
   * evicting the last item of a type discards that placeholder before the diff can run, and the
   * stale entry is never cleaned up.
   *
   * <p>This is safe against KubernetesCachingAgent#loadPrimaryResourceList reporting a spuriously
   * empty kind: a real listing failure (including "forbidden" permission errors -- see {@link
   * com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor#listAuthoritative})
   * throws and is excluded from the placeholder backfill, rather than masquerading as zero live
   * resources.
   */
  @Override
  public boolean supportsFullEviction() {
    return true;
  }
}
