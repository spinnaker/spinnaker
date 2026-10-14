/*
 * Copyright 2026 Spinnaker Contributors
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

package com.netflix.kayenta.canary.providers.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Exercises the interface defaults for an implementation that predates the single {@code template}
 * field: it only knows {@code getCustomInlineTemplate}, {@code getCustomFilterTemplate} and {@code
 * cloneWithEscapedInlineTemplate}, and does not extend {@link AbstractCanaryMetricSetQueryConfig}.
 */
@SuppressWarnings("deprecation")
public class LegacyCanaryMetricSetQueryConfigTest {

  private static class LegacyQueryConfig implements CanaryMetricSetQueryConfig {
    private final String inline;
    private final String named;
    boolean escapeCalled;

    LegacyQueryConfig(String inline, String named) {
      this.inline = inline;
      this.named = named;
    }

    @Override
    public String getCustomInlineTemplate() {
      return inline;
    }

    @Override
    public String getCustomFilterTemplate() {
      return named;
    }

    @Override
    public CanaryMetricSetQueryConfig cloneWithEscapedInlineTemplate() {
      escapeCalled = true;
      return this;
    }

    @Override
    public String getServiceType() {
      return "legacy";
    }
  }

  @Test
  public void getTemplate_defaultResolvesInlineTemplateAndUnescapes() {
    LegacyQueryConfig query = new LegacyQueryConfig("a $\\{scope}", null);

    assertThat(query.getTemplate(CanaryConfig.builder().build())).isEqualTo("a ${scope}");
  }

  @Test
  public void getTemplate_defaultResolvesNamedTemplate() {
    LegacyQueryConfig query = new LegacyQueryConfig(null, "named");
    CanaryConfig config =
        CanaryConfig.builder().templates(Collections.singletonMap("named", "n $\\{scope}")).build();

    assertThat(query.getTemplate(config)).isEqualTo("n ${scope}");
  }

  @Test
  public void getTemplate_defaultReturnsNullWhenNothingResolves() {
    assertThat(new LegacyQueryConfig(null, "named").getTemplate(CanaryConfig.builder().build()))
        .isNull();
    assertThat(new LegacyQueryConfig(null, null).getTemplate(null)).isNull();
  }

  @Test
  public void cloneWithEscapedTemplate_defaultsToCloneWithEscapedInlineTemplate() {
    LegacyQueryConfig query = new LegacyQueryConfig("x", null);

    query.cloneWithEscapedTemplate();

    assertThat(query.escapeCalled).isTrue();
  }

  @Test
  public void expandCustomFilter_expandsLegacyImplementationsInlineTemplate() {
    LegacyQueryConfig query = new LegacyQueryConfig("scope=${scope}", null);
    CanaryScope scope = CanaryScope.builder().scope("my-asg").build();

    assertThat(
            QueryConfigUtils.expandCustomFilter(
                CanaryConfig.builder().build(), query, scope, new String[] {"scope"}))
        .isEqualTo("scope=my-asg");
  }

  @Test
  public void expandCustomFilter_throwsWhenNamedTemplateNotFound() {
    LegacyQueryConfig query = new LegacyQueryConfig(null, "missing");
    CanaryConfig config =
        CanaryConfig.builder().templates(Collections.singletonMap("other", "x")).build();

    assertThatThrownBy(
            () ->
                QueryConfigUtils.expandCustomFilter(
                    config, query, CanaryScope.builder().build(), new String[] {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'missing' was not found");
  }

  @Test
  public void expandCustomFilter_throwsWhenNamedTemplateReferencedButNoTemplatesDefined() {
    LegacyQueryConfig query = new LegacyQueryConfig(null, "missing");

    assertThatThrownBy(
            () ->
                QueryConfigUtils.expandCustomFilter(
                    CanaryConfig.builder().build(),
                    query,
                    CanaryScope.builder().build(),
                    new String[] {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no templates were defined");
  }
}
