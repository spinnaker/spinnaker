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
 */

package simplestage;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.api.simplestage.SimpleStage;
import com.netflix.spinnaker.orca.api.simplestage.SimpleStageInput;
import com.netflix.spinnaker.orca.api.simplestage.SimpleStageOutput;
import com.netflix.spinnaker.orca.api.simplestage.SimpleStageStatus;
import java.util.Collections;
import org.pf4j.Extension;

/**
 * A plain example of the {@link SimpleStage} extension point.
 *
 * <p>This stage takes two optional input fields and emits a message.
 *
 * <pre>{@code
 * {
 *   "type": "simple",
 *   "recipient": "Programmer person",
 *   "message": "This message is from the simple stage. Wow!"
 * }
 * }</pre>
 */
@Extension
public class SimpleStageSample implements SimpleStage<SimpleStageSample.Input> {

  @Override
  public SimpleStageOutput execute(SimpleStageInput<Input> simpleStageInput) {
    final Output output = new Output();

    output.setOutput(
        format(
            "recipient=%s message=%s",
            simpleStageInput.getValue().recipient, simpleStageInput.getValue().message));
    output.setContext(Collections.emptyMap());
    output.setStatus(SimpleStageStatus.SUCCEEDED);

    return output;
  }

  @Override
  public String getName() {
    return "simple";
  }

  public static class Input {
    public String recipient;
    public String message;
  }

  public static class Output extends SimpleStageOutput<String, Object> {}
}
