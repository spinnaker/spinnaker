/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.kork.secrets;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import javax.annotation.Nullable;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * Wraps an enumerable property source with support for decrypting {@link EncryptedSecret} URIs
 * found in property values.
 *
 * @param <T> underlying source of properties being wrapped
 */
@NonnullByDefault
public class SecretAwarePropertySource<T> extends EnumerablePropertySource<T> {
  private final EnumerablePropertySource<T> delegate;
  private final SecretPropertyProcessor secretPropertyProcessor;

  SecretAwarePropertySource(
      EnumerablePropertySource<T> source, SecretPropertyProcessor secretPropertyProcessor) {
    super(source.getName(), source.getSource());
    this.delegate = source;
    this.secretPropertyProcessor = secretPropertyProcessor;
  }

  @Override
  @Nullable
  public Object getProperty(String name) {
    return secretPropertyProcessor.processPropertyValue(name, delegate.getProperty(name));
  }

  @Override
  public String[] getPropertyNames() {
    return delegate.getPropertyNames();
  }

  @Override
  public boolean containsProperty(String name) {
    return delegate.containsProperty(name);
  }

  public EnumerablePropertySource<T> getDelegate() {
    return delegate;
  }
}
