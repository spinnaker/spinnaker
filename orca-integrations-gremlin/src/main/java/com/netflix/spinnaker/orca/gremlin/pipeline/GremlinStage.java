package com.netflix.spinnaker.orca.gremlin.pipeline;

import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.gremlin.GremlinService;
import com.netflix.spinnaker.orca.gremlin.tasks.LaunchGremlinAttackTask;
import com.netflix.spinnaker.orca.gremlin.tasks.MonitorGremlinAttackTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;

@Component
public class GremlinStage implements StageDefinitionBuilder, CancellableStage {
  public final static String APIKEY_KEY = "gremlinApiKey";
  public final static String COMMAND_TEMPLATE_ID_KEY = "gremlinCommandTemplateId";
  public final static String TARGET_TEMPLATE_ID_KEY = "gremlinTargetTemplateId";
  public final static String GUID_KEY = "gremlinAttackGuid";
  public final static String TERMINAL_KEY = "isGremlinTerminal";

  @Autowired
  private GremlinService gremlinService;

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("launchGremlinAttack", LaunchGremlinAttackTask.class)
      .withTask("monitorGremlinAttack", MonitorGremlinAttackTask.class);
  }

  @Override
  public Result cancel(Stage stage) {
    final Map<String, Object> ctx = stage.getContext();
    final boolean isAttackCompleted = Optional.ofNullable(ctx.get(TERMINAL_KEY))
      .map(s -> {
        try {
          return Boolean.parseBoolean((String) s);
        } catch (final Exception ex) {
          return false;
        }
      })
      .orElse(false);

    if (!isAttackCompleted) {
      gremlinService.haltAttack(getApiKey(ctx), getAttackGuid(ctx));
      return new CancellableStage.Result(stage, ctx);
    }
    return null;
  }

  public static String getApiKey(final Map<String, Object> ctx) {
    final String apiKey = (String) ctx.get(APIKEY_KEY);
    if (apiKey == null || apiKey.isEmpty()) {
      throw new RuntimeException("No API Key provided");
    } else {
      return "Key " + apiKey;
    }
  }

  public static String getAttackGuid(final Map<String, Object> ctx) {
    final String guid = (String) ctx.get(GUID_KEY);
    if (guid == null || guid.isEmpty()) {
      throw new RuntimeException("Could not find an active Gremlin attack GUID");
    } else {
      return guid;
    }
  }
}
