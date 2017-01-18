/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.provider.v1;

import com.netflix.spinnaker.halyard.deploy.component.v1.ComponentService;
import com.netflix.spinnaker.halyard.deploy.component.v1.ComponentType;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.deploy.job.v1.JobStatus;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KubernetesProvider extends Provider {
  @Value("${deploy.kubernetes.minPollSeconds:1}")
  private int MIN_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.maxPollSeconds:16}")
  private int MAX_POLL_INTERVAL_SECONDS;

  @Value("${deploy.kubernetes.pollTimeout:10}")
  private int TIMEOUT_MINUTES;
  
  private static final String CLOUDRIVER_CONFIG_PATH = "/kubernetes/raw/hal-clouddriver.yml";

  @Override
  public ComponentService connectTo(ComponentType componentType) {
    JobRequest request = new JobRequest().setTokenizedCommand(Arrays.asList("kubectl", "proxy"));

    String jobId = jobExecutor.startJob(request);

    return null;
  }

  @Override
  public void bootstrapClouddriver() {
    JobRequest request = new JobRequest()
        .setTokenizedCommand(Arrays.asList("kubectl", "create", "-f", "-"))
        .setTimeoutMillis(TimeUnit.MINUTES.toMillis(TIMEOUT_MINUTES));

    InputStream cloudddriverConfig = getClass().getResourceAsStream(CLOUDRIVER_CONFIG_PATH);

    String jobId = jobExecutor.startJob(request, System.getenv(), cloudddriverConfig);

    JobStatus jobStatus = jobExecutor.backoffWait(jobId,
        TimeUnit.SECONDS.toMillis(MIN_POLL_INTERVAL_SECONDS),
        TimeUnit.SECONDS.toMillis(MAX_POLL_INTERVAL_SECONDS));

    System.out.println(jobStatus.getStdOut());
    System.out.println(jobStatus.getStdErr());
  }
}
