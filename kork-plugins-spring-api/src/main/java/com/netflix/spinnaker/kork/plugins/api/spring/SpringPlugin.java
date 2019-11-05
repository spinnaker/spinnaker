/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.api.spring;

import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Allows a plugin to use its own Spring {@link ApplicationContext}.
 *
 * <p>This can be used in larger plugins that need to manage a lot of moving parts.
 */
@Alpha
public abstract class SpringPlugin extends Plugin {

  private AnnotationConfigApplicationContext applicationContext;

  /**
   * Constructor to be used by plugin manager for plugin instantiation. Your plugins have to provide
   * constructor with this exact signature to be successfully loaded by manager.
   *
   * @param wrapper
   */
  public SpringPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  /** This method is called after the plugin has started. */
  public abstract void initApplicationContext();

  /** @param applicationContext The {@link ApplicationContext} for the plugin. */
  public void setApplicationContext(AnnotationConfigApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @VisibleForTesting
  public AnnotationConfigApplicationContext getApplicationContext() {
    return this.applicationContext;
  }

  @Override
  public void stop() {
    if (applicationContext != null) {
      applicationContext.stop();
    }
  }
}
