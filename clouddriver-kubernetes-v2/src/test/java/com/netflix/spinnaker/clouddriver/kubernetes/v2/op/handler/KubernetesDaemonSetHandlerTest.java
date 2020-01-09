/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesDaemonSetHandlerTest {
  private static final Gson gson = new Gson();

  private static KubernetesManifest baseDaemonSet() throws IOException {
    String text =
        Resources.toString(
            KubernetesDaemonSetHandler.class.getResource("daemonsetbase.json"),
            StandardCharsets.UTF_8);
    return gson.fromJson(text, KubernetesManifest.class);
  }

  private static KubernetesManifest daemonSet(Map<String, Object> status) throws IOException {
    KubernetesManifest daemonSet = baseDaemonSet();
    daemonSet.put("status", status);
    // The logic in KubernetesManifest expects that numeric values are deserialized as Doubles, and
    // fails when these are Integers.  To avoid having code in this test that works around the issue
    // (such as by inserting 1.0 as the observedGeneration) just serialize then deserialize the
    // updated manifest so the test is getting a manifest that is produced exactly as would happen
    // in real use cases when clouddriver fetches the manifest from kubernetes.
    return gson.fromJson(gson.toJson(daemonSet), KubernetesManifest.class);
  }

  @Test
  public void unstableWhenUnavailable() throws IOException {
    KubernetesDaemonSetHandler daemonSetHandler = new KubernetesDaemonSetHandler();
    KubernetesManifest manifest =
        daemonSet(
            ImmutableMap.<String, Object>builder()
                .put("currentNumberScheduled", 1)
                .put("desiredNumberScheduled", 1)
                .put("numberMisscheduled", 0)
                .put("numberReady", 0)
                .put("numberUnavailable", 1)
                .put("observedGeneration", 1)
                .put("updatedNumberScheduled", 1)
                .build());
    Status status = daemonSetHandler.status(manifest);

    assertThat(status.getStable().isState()).isFalse();
  }

  @Test
  public void stableWhenAllAvailable() throws IOException {
    KubernetesDaemonSetHandler daemonSetHandler = new KubernetesDaemonSetHandler();
    KubernetesManifest manifest =
        daemonSet(
            ImmutableMap.<String, Object>builder()
                .put("currentNumberScheduled", 1)
                .put("desiredNumberScheduled", 1)
                .put("numberAvailable", 1)
                .put("numberMisscheduled", 0)
                .put("numberReady", 1)
                .put("observedGeneration", 1)
                .put("updatedNumberScheduled", 1)
                .build());
    Status status = daemonSetHandler.status(manifest);

    assertThat(status.getStable().isState()).isTrue();
  }

  @Test
  public void stableWhenNoneDesired() throws IOException {
    KubernetesDaemonSetHandler daemonSetHandler = new KubernetesDaemonSetHandler();
    KubernetesManifest manifest =
        daemonSet(
            ImmutableMap.<String, Object>builder()
                .put("currentNumberScheduled", 0)
                .put("desiredNumberScheduled", 0)
                .put("numberMisscheduled", 0)
                .put("numberReady", 0)
                .put("observedGeneration", 1)
                .build());
    Status status = daemonSetHandler.status(manifest);

    assertThat(status.getStable().isState()).isTrue();
  }
}
