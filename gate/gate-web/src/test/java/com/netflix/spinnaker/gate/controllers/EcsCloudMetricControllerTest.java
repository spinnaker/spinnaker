package com.netflix.spinnaker.gate.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.controllers.ecs.EcsCloudMetricController;
import com.netflix.spinnaker.gate.services.EcsCloudMetricService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import retrofit2.Call;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
public class EcsCloudMetricControllerTest {

  private MockWebServer server;
  private MockMvc mockMvc;

  @Mock private ClouddriverService clouddriverService;

  private EcsCloudMetricService ecsCloudMetricService;

  private ObjectMapper objectMapper;

  @BeforeEach
  public void setup() {
    server = new MockWebServer();
    ecsCloudMetricService = new EcsCloudMetricService(clouddriverService);
    EcsCloudMetricController controller = new EcsCloudMetricController();
    controller.setEcsClusterService(ecsCloudMetricService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
  }

  @AfterEach
  public void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  public void shouldReturnEmptyResponse() throws Exception {
    Call<List<Map>> callResponse = Calls.response(new ArrayList<>());
    List<Map> alarmsList = new ArrayList<>();

    when(clouddriverService.getEcsAllMetricAlarms()).thenReturn(callResponse);

    mockMvc
        .perform(get("/ecs/cloudMetrics/alarms").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect((content().json(objectMapper.writeValueAsString(alarmsList))));

    verify(clouddriverService).getEcsAllMetricAlarms();
  }

  @Test
  public void shouldReturnNonEmptyResponse() throws Exception {
    List<Map> alarmsList = new ArrayList<>();

    Map<String, String> alarm1 = new HashMap<>();
    alarm1.put("accountName", "account1");
    alarm1.put(
        "alarmActions",
        "arn:aws:autoscaling:us-west-2:0000000000000:scalingPolicy:"
            + "981fc192-4b43-46f7-963a-2c7e6bd9b9ce:autoScalingGroupName/my-ecs-cluster:policyName/"
            + "ECSManagedAutoScalingPolicy-ea823f38-166f-497d-bc13-5ef1aaca62ad");
    alarm1.put(
        "alarmArn",
        "arn:aws:cloudwatch:us-west-2:0000000000000:alarm:"
            + "TargetTracking-my-ecs-cluster-AlarmHigh-8d1ecfc9-5e98-4011-8a8b-c5ebfa7742eb");
    alarm1.put(
        "alarmName",
        "TargetTracking-my-ecs-cluster-AlarmHigh-8d1ecfc9-5e98-4011-8a8b-c5ebfa7742eb");
    alarm1.put("dimensions", "[]");
    alarm1.put("insufficientDataActions", "[]");
    alarm1.put("metrics", "[]");
    alarm1.put("region", "us-west-2");

    Map<String, String> alarm2 = new HashMap<>();
    alarm2.put("accountName", "account2");
    alarm2.put(
        "alarmActions",
        "arn:aws:autoscaling:us-west-2:11111111111111:scalingPolicy:"
            + "981fc192-4b43-46f7-963a-2c7e6bd9b9ce:autoScalingGroupName/my-ecs-cluster:policyName/"
            + "ECSManagedAutoScalingPolicy-ea823f38-166f-497d-bc13-5ef1aaca62ad");
    alarm2.put(
        "alarmArn",
        "arn:aws:cloudwatch:us-west-2:11111111111111:alarm:"
            + "TargetTracking-my-ecs-cluster-AlarmHigh-8d1ecfc9-5e98-4011-8a8b-c5ebfa7742eb");
    alarm2.put(
        "alarmName",
        "TargetTracking-my-ecs-cluster-AlarmHigh-8d1ecfc9-5e98-4011-8a8b-c5ebfa7742eb");
    alarm2.put("dimensions", "[]");
    alarm2.put("insufficientDataActions", "[]");
    alarm2.put("metrics", "[]");
    alarm2.put("region", "us-west-2");

    alarmsList.add(alarm1);
    alarmsList.add(alarm2);

    Call<List<Map>> callResponse = Calls.response(alarmsList);
    when(clouddriverService.getEcsAllMetricAlarms()).thenReturn(callResponse);

    mockMvc
        .perform(get("/ecs/cloudMetrics/alarms").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect((content().json(objectMapper.writeValueAsString(alarmsList))));

    verify(clouddriverService).getEcsAllMetricAlarms();
  }
}
