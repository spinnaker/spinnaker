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

package com.netflix.spectator.stackdriver;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.google.api.services.monitoring.v3.model.MonitoredResource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MonitoredResourceBuilderTest {

  MonitoredResourceBuilder builder;

  @Before
  public void setup() {
    builder = spy(new MonitoredResourceBuilder());
  }

  @Test
  public void testGceInstance() throws IOException {
    builder.setStackdriverProject("UNUSED");

    String instance = "MY INSTANCE";
    String zone = "us-central1-f";
    String zone_path = "path/to/" + zone;
    String project = "MY PROJECT";

    doReturn(instance).when(builder).getGoogleMetadataValue("instance/id");
    doReturn(zone_path).when(builder).getGoogleMetadataValue("instance/zone");
    doReturn(project).when(builder).getGoogleMetadataValue("project/project-id");

    Map<String, String> labels = new HashMap<String, String>();
    labels.put("instance_id", instance);
    labels.put("zone", zone);

    MonitoredResource resource = builder.build();
    Assert.assertEquals("gce_instance", resource.getType());
    Assert.assertEquals(labels, resource.getLabels());
  }

  @Test
  public void testMatchAttribute() {
    String text =
        "{\n"
            + " \"version\" : \"2016-08-01\",\n"
            + " \"instanceId\" : \"the-instance\",\n"
            + " \"region\" : \"us-east-1\"\n"
            + "}";

    Assert.assertEquals("the-instance", builder.matchAttribute(text, "instanceId"));
    Assert.assertEquals("us-east-1", builder.matchAttribute(text, "region"));
    Assert.assertEquals("", builder.matchAttribute(text, "notFound"));
  }

  @Test
  public void testEc2Instance() throws IOException {
    String region = "us-east-1";
    String instanceId = "i-abcdef";
    String accountId = "12345";
    String project = "StackdriverProject";

    builder.setStackdriverProject(project);

    String awsIdentityDoc =
        "{\n"
            + "\"privateIp\" : \"123.45.67.89\",\n"
            + "\"devpayProductCodes\" : null,\n"
            + "\"availabilityZone\" : \"us-east-1d\",\n"
            + "\"accountId\" : \""
            + accountId
            + "\",\n"
            + "\"version\" : \"2010-08-31\",\n"
            + "\"instanceId\" : \""
            + instanceId
            + "\",\n"
            + "\"billingProducts\" : null,\n"
            + "\"region\" : \""
            + region
            + "\"\n"
            + "}";

    doThrow(new IOException()).when(builder).getGoogleMetadataValue(any(String.class));

    doReturn(awsIdentityDoc).when(builder).getAwsIdentityDocument();

    Map<String, String> labels = new HashMap<String, String>();
    labels.put("instance_id", instanceId);
    labels.put("aws_account", accountId);
    labels.put("region", region);
    labels.put("project_id", project);

    MonitoredResource resource = builder.build();
    Assert.assertEquals("aws_ec2_instance", resource.getType());
    Assert.assertEquals(labels, resource.getLabels());
  }

  @Test
  public void testGlobal() throws IOException {
    String project = "StackdriverProject";
    builder.setStackdriverProject(project);

    doThrow(new IOException()).when(builder).getGoogleMetadataValue(any(String.class));
    doThrow(new IOException()).when(builder).getAwsIdentityDocument();

    Map<String, String> labels = new HashMap<String, String>();
    labels.put("project_id", project);

    MonitoredResource resource = builder.build();
    Assert.assertEquals("global", resource.getType());
    Assert.assertEquals(labels, resource.getLabels());
  }
}
