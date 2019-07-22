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

package com.netflix.spinnaker.kork.plugins.spring;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

// TODO: add test(s) that load a plugin jar
public class SpinnakerApplicationSystemTest {

  @Test
  public void shouldRegisterSubtypesByClass() throws MalformedURLException {
    URL expected = Paths.get("/opt/spinnaker/plugins/plugin-1.2.3.jar").toUri().toURL();
    URL[] initialClasspath =
        ((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs();
    new TestApplication().main();
    URL[] finalClasspath =
        ((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs();
    Assert.assertEquals((initialClasspath.length + 1), finalClasspath.length);
    Assert.assertEquals(expected, finalClasspath[finalClasspath.length - 1]);
  }

  private class TestApplication extends SpinnakerApplication {

    public final Map<String, Object> DEFAULT_PROPS =
        new HashMap<String, Object>() {
          {
            put("spring.main.web-application-type", "none");
          }
        };

    void main(String... args) {
      PluginLoader pluginLoader = new PluginLoader("./src/test/resources/plugins.yml");
      SpinnakerApplication.initialize(pluginLoader, DEFAULT_PROPS, TestApplication.class, args);
    }
  }
}
