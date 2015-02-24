/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.retrofit.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit.RestAdapter

import java.lang.reflect.Method

class RetrofitSlf4jLog implements RestAdapter.Log {
  private final Logger logger
  private final Level level


  RetrofitSlf4jLog(Class clazz, Level level = Level.INFO) {
    this(LoggerFactory.getLogger(clazz), level)
  }

  RetrofitSlf4jLog(Logger logger, Level level = Level.INFO) {
    this.logger = logger
    this.level = level
  }

  @Override
  void log(String message) {
    level.log(logger, message)
  }

  public static enum Level {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE

    private Method getLoggerMethod() {
      Logger.getMethod(this.name().toLowerCase(), String)
    }

    public void log(Logger logger, String message) {
      getLoggerMethod().invoke(logger, message)
    }
  }
}
