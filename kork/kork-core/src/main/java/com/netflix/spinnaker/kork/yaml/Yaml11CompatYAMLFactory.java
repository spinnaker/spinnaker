/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.yaml;

import java.io.CharArrayReader;
import java.io.InputStream;
import java.io.Reader;
import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.io.IOContext;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLFactoryBuilder;
import tools.jackson.dataformat.yaml.YAMLParser;

/**
 * A {@link YAMLFactory} whose parsers read plain scalars the way Jackson 2 did (YAML 1.1 booleans,
 * nulls and number formats), so untyped YAML such as pipeline templates keeps its meaning.
 */
public class Yaml11CompatYAMLFactory extends YAMLFactory {

  public Yaml11CompatYAMLFactory(YAMLFactoryBuilder builder) {
    super(builder);
  }

  protected Yaml11CompatYAMLFactory(Yaml11CompatYAMLFactory src) {
    super(src);
  }

  @Override
  public YAMLFactory copy() {
    return new Yaml11CompatYAMLFactory(this);
  }

  @Override
  public TokenStreamFactory snapshot() {
    return new Yaml11CompatYAMLFactory(this);
  }

  private YAMLParser parser(ObjectReadContext readCtxt, IOContext ioCtxt, Reader reader) {
    return new Yaml11CompatYAMLParser(
        readCtxt,
        ioCtxt,
        _getBufferRecycler(),
        readCtxt.getStreamReadFeatures(_streamReadFeatures),
        readCtxt.getFormatReadFeatures(_formatReadFeatures),
        _loadSettings,
        reader);
  }

  @Override
  protected YAMLParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt, InputStream in) {
    return parser(readCtxt, ioCtxt, _createReader(in, null, ioCtxt));
  }

  @Override
  protected YAMLParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt, Reader r) {
    return parser(readCtxt, ioCtxt, r);
  }

  @Override
  protected YAMLParser _createParser(
      ObjectReadContext readCtxt,
      IOContext ioCtxt,
      char[] data,
      int offset,
      int len,
      boolean recyclable)
      throws JacksonException {
    return parser(readCtxt, ioCtxt, new CharArrayReader(data, offset, len));
  }

  @Override
  protected YAMLParser _createParser(
      ObjectReadContext readCtxt, IOContext ioCtxt, byte[] data, int offset, int len) {
    return parser(readCtxt, ioCtxt, _createReader(data, offset, len, null, ioCtxt));
  }
}
