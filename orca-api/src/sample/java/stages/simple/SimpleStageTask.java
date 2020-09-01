/*
 * Copyright 2020 Netflix, Inc.
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
package stages.simple;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.pf4j.Extension;

@Extension
public class SimpleStageTask implements Task {
  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    MyStageInput input = stage.mapTo(MyStageInput.class);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .output(
            "simpleMessage",
            format(
                "%s, %s!",
                Optional.ofNullable(input.message).orElse("Hello"),
                Optional.ofNullable(input.recipient).orElse("world")))
        .build();
  }

  private static class MyStageInput {
    public String message;
    public String recipient;
  }
}
