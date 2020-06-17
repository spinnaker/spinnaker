/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.kork.expressions.allowlist;

import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.StandardTypeLocator;

public class AllowListTypeLocator implements TypeLocator {
  private final InstantiationTypeRestrictor instantiationTypeRestrictor =
      new InstantiationTypeRestrictor();
  private final TypeLocator delegate = new StandardTypeLocator();

  @Override
  public Class<?> findType(String typeName) throws EvaluationException {
    Class<?> type = delegate.findType(typeName);
    if (instantiationTypeRestrictor.supports(type)) {
      return type;
    }
    throw new SpelEvaluationException(SpelMessage.EXCEPTION_DURING_PROPERTY_READ, typeName);
  }
}
