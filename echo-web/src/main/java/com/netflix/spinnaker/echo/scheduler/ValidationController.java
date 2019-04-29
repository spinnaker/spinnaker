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

package com.netflix.spinnaker.echo.scheduler;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.echo.cron.CronExpressionFuzzer;
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.InvalidCronExpressionException;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import net.redhogs.cronparser.CronExpressionDescriptor;
import net.redhogs.cronparser.Options;
import org.quartz.CronExpression;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class ValidationController {

  @RequestMapping(value = "/validateCronExpression", method = RequestMethod.GET)
  @ResponseStatus(value = HttpStatus.OK)
  public Map<String, Object> validateCronExpression(@RequestParam String cronExpression) {
    ImmutableMap.Builder<String, Object> mapBuilder = ImmutableMap.builder();

    try {
      if (cronExpression == null) {
        throw new InvalidCronExpressionException("null", "cron expression can't be null");
      }

      new CronExpression(CronExpressionFuzzer.fuzz("", cronExpression));

      mapBuilder.put("response", "Cron expression is valid");
      if (CronExpressionFuzzer.hasFuzzyExpression(cronExpression)) {
        mapBuilder.put("description", "No description available for fuzzy cron expressions");
      } else {
        try {
          Options options = new Options();
          options.setZeroBasedDayOfWeek(false);
          mapBuilder.put(
              "description", CronExpressionDescriptor.getDescription(cronExpression, options));
        } catch (ParseException IGNORED) {
          mapBuilder.put("description", "No description available");
        }
      }

      return mapBuilder.build();
    } catch (ParseException e) {
      throw new InvalidCronExpressionException(cronExpression, e.getMessage());
    }
  }

  @SuppressWarnings("unused")
  @ExceptionHandler(InvalidCronExpressionException.class)
  void handleInvalidCronExpression(HttpServletResponse response, InvalidCronExpressionException e)
      throws IOException {
    response.sendError(HttpStatus.BAD_REQUEST.value(), e.getMessage());
  }
}
