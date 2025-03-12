/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.web.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

public class DefaultThreadUncaughtExceptionHandler
    implements Thread.UncaughtExceptionHandler,
        ApplicationListener<ApplicationEnvironmentPreparedEvent> {
  private static final Logger logger =
      LoggerFactory.getLogger(DefaultThreadUncaughtExceptionHandler.class);
  private Thread.UncaughtExceptionHandler priorHandler;

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    boolean isEnabled =
        event.getEnvironment().getProperty("globalExceptionHandlingEnabled", boolean.class, true);

    if (isEnabled) {
      priorHandler = Thread.getDefaultUncaughtExceptionHandler();
      Thread.setDefaultUncaughtExceptionHandler(this);
    }
  }

  @Override
  public void uncaughtException(Thread thread, Throwable exception) {
    logger.error("Uncaught exception in thread", exception);

    if (priorHandler != null) {
      priorHandler.uncaughtException(thread, exception);
    }
  }
}
