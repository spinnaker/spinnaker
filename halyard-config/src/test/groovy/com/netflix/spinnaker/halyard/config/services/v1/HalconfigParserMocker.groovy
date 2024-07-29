/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.services.v1

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser
import com.netflix.spinnaker.halyard.config.config.v1.StrictObjectMapper
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig
import org.springframework.context.ApplicationContext
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class HalconfigParserMocker extends Specification {
  HalconfigParser mockHalconfigParser(String config) {
    ApplicationContext applicationContext = Stub(ApplicationContext.class)
    applicationContext.getBean(Yaml.class) >> new Yaml(new SafeConstructor())

    // HalconfigDirectoryStructure needs a non-null halconfigDirectory for
    // Paths.get(getHalconfigDirectory()) to work properly.  The directory
    // doesn't actually need to exist though.  So, instead of
    //
    // String halconfigDirectory = Files.createTempDirectory("halyard-test")
    //
    // let's use an arbitrary string
    String halconfigDirectory = "halyard-test"
    def parserStub = new HalconfigParser(new StrictObjectMapper(), new HalconfigDirectoryStructure(halconfigDirectory), applicationContext)

    def stream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))
    Halconfig halconfig = parserStub.parseHalconfig(stream)
    halconfig = parserStub.transformHalconfig(halconfig)
    HalconfigParser parser = Mock(HalconfigParser)
    parser.getHalconfig() >> halconfig
    return parser
  }
}
