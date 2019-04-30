package com.netflix.spinnaker.orca.gremlin.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.gremlin.AttackParameters;
import com.netflix.spinnaker.orca.gremlin.GremlinService;
import com.netflix.spinnaker.orca.gremlin.pipeline.GremlinStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.orca.gremlin.pipeline.GremlinStage.COMMAND_TEMPLATE_ID_KEY;
import static com.netflix.spinnaker.orca.gremlin.pipeline.GremlinStage.GUID_KEY;
import static com.netflix.spinnaker.orca.gremlin.pipeline.GremlinStage.TARGET_TEMPLATE_ID_KEY;

@Component
public class LaunchGremlinAttackTask implements Task {
  private static final String GREMLIN_TEMPLATE_ID_KEY = "template_id";

  @Autowired
  private GremlinService gremlinService;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    final Map<String, Object> ctx = stage.getContext();

    final String apiKey = GremlinStage.getApiKey(ctx);

    final String commandTemplateId = (String) ctx.get(COMMAND_TEMPLATE_ID_KEY);
    if (commandTemplateId == null || commandTemplateId.isEmpty()) {
      throw new RuntimeException("No command template provided");
    }

    final String targetTemplateId = (String) ctx.get(TARGET_TEMPLATE_ID_KEY);
    if (targetTemplateId == null || targetTemplateId.isEmpty()) {
      throw new RuntimeException("No target template provided");
    }

    final Map<String, Object> commandViaTemplate = new HashMap<>();
    commandViaTemplate.put(GREMLIN_TEMPLATE_ID_KEY, commandTemplateId);

    final Map<String, Object> targetViaTemplate = new HashMap<>();
    targetViaTemplate.put(GREMLIN_TEMPLATE_ID_KEY, targetTemplateId);

    final AttackParameters newAttack = new AttackParameters(commandViaTemplate, targetViaTemplate);

    final String createdGuid = gremlinService.create(apiKey, newAttack);
    final Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(GUID_KEY, createdGuid);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(responseMap).build();
  }
}
