package com.netflix.kayenta.datadog.orca;

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class DatadogFetchStage {
  @Bean
  StageDefinitionBuilder datadogFetchStageBuilder() {
    return new StageDefinitionBuilder() {
      @Override
      public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
        builder.withTask("datadogFetch", DatadogFetchTask.class);
      }

      @Nonnull
      @Override
      public String getType() {
        return "datadogFetch";
      }
    };
  }
}
