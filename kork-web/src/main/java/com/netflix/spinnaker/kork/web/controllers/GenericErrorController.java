/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.kork.web.controllers;

import com.netflix.spinnaker.kork.web.exceptions.HasAdditionalAttributes;
import org.springframework.boot.autoconfigure.web.ErrorAttributes;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class GenericErrorController implements ErrorController {
  private final ErrorAttributes errorAttributes;

  public GenericErrorController(ErrorAttributes errorAttributes) {
    this.errorAttributes = errorAttributes;
  }

  @RequestMapping(value = "/error")
  public Map error(@RequestParam(value = "trace", defaultValue = "false") Boolean includeStackTrace,
                   HttpServletRequest httpServletRequest) {
    ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(httpServletRequest);

    Map<String, Object> attributes = errorAttributes.getErrorAttributes(servletRequestAttributes, includeStackTrace);

    Throwable exception = errorAttributes.getError(servletRequestAttributes);
    if (exception != null && exception instanceof HasAdditionalAttributes) {
      attributes.putAll(((HasAdditionalAttributes) exception).getAdditionalAttributes());
    }

    return attributes;
  }

  @Override
  public String getErrorPath() {
    return "/error";
  }
}
