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

import java.io.Reader;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.dataformat.yaml.YAMLParser;

/**
 * A {@link YAMLParser} that reads plain scalars the way Jackson 2 did; see {@link Yaml11Scalars}.
 */
class Yaml11CompatYAMLParser extends YAMLParser {

  Yaml11CompatYAMLParser(
      ObjectReadContext readCtxt,
      IOContext ioCtxt,
      BufferRecycler recycler,
      int parserFeatures,
      int formatFeatures,
      LoadSettings loadSettings,
      Reader reader) {
    super(readCtxt, ioCtxt, recycler, parserFeatures, formatFeatures, loadSettings, reader);
  }

  @Override
  protected JsonToken _decodeScalar(ScalarEvent event) throws JacksonException {
    if (event.isPlain() && event.getTag().isEmpty()) {
      String normalized;
      try {
        normalized = Yaml11Scalars.normalize(event.getValue(), streamReadConstraints());
      } catch (NumberFormatException e) {
        throw _constructReadException("Invalid YAML numeric scalar: " + event.getValue());
      }
      if (normalized != null) {
        JsonToken token =
            super._decodeScalar(
                new ScalarEvent(
                    event.getAnchor(),
                    event.getTag(),
                    event.getImplicit(),
                    normalized,
                    event.getScalarStyle(),
                    event.getStartMark(),
                    event.getEndMark()));
        // Numeric decoding uses _cleanedTextValue; string coercion must retain the YAML spelling.
        _textValue = event.getValue();
        return token;
      }
    }
    return super._decodeScalar(event);
  }
}
