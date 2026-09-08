/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.kork.expressions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DashedIdentifierResolverTest {

  @Test
  void rewritesASingleHyphenatedBareword() {
    Optional<String> rewritten =
        DashedIdentifierResolver.rewriteHyphenatedBareword("${my-container-name}");

    assertThat(rewritten).contains("${#root['my-container-name']}");
  }

  @Test
  void trimsWhitespaceInsideTheTemplateDelimiters() {
    Optional<String> rewritten =
        DashedIdentifierResolver.rewriteHyphenatedBareword("${  my-container-name  }");

    assertThat(rewritten).contains("${#root['my-container-name']}");
  }

  @Test
  void supportsMultipleHyphensInASingleIdentifier() {
    Optional<String> rewritten =
        DashedIdentifierResolver.rewriteHyphenatedBareword("${my-really-long-container-name}");

    assertThat(rewritten).contains("${#root['my-really-long-container-name']}");
  }

  @Test
  void doesNotRewriteAnExpressionWithNoHyphen() {
    Optional<String> rewritten =
        DashedIdentifierResolver.rewriteHyphenatedBareword("${mycontainer}");

    assertThat(rewritten).isEmpty();
  }

  @Test
  void doesNotRewriteRealArithmeticSubtraction() {
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword("${5-2}")).isEmpty();
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword("${trigger.buildNumber - 1}"))
        .isEmpty();
  }

  @Test
  void doesNotRewriteADottedPropertyChainWithATrailingHyphenatedSegment() {
    // Known limitation: compound paths mixing '.' and '-' are not yet supported.
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword("${metadata.labels.app-name}"))
        .isEmpty();
  }

  @Test
  void doesNotRewriteAStringThatIsNotEntirelyASingleTemplateExpression() {
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword("prefix-${a-b}-suffix"))
        .isEmpty();
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword("not a template")).isEmpty();
  }

  @Test
  void returnsEmptyForNullOrBlankInput() {
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword(null)).isEmpty();
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword("")).isEmpty();
  }

  @Test
  void doesNotRewriteAnIdentifierStartingWithADigit() {
    // Starts with a digit, so it's ambiguous with a numeric literal minus a property - leave it
    // as real SpEL arithmetic.
    assertThat(DashedIdentifierResolver.rewriteHyphenatedBareword("${1-name}")).isEmpty();
  }
}
