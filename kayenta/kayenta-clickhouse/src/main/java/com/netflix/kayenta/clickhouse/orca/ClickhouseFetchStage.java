package com.netflix.kayenta.clickhouse.orca;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import javax.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ClickhouseFetchStage {

  @Bean
  StageDefinitionBuilder clickhouseFetchStageBuilder() {
    return new StageDefinitionBuilder() {
      @Override
      public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
        builder.withTask("clickhouseFetch", ClickhouseFetchTask.class);
      }

      @Nonnull
      @Override
      public String getType() {
        return "clickhouseFetch";
      }
    };
  }
}
