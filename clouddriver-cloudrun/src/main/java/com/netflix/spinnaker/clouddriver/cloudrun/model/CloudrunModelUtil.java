/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudrunModelUtil {

  private static final List<SimpleDateFormat> dateFormats =
      Stream.of("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
          .map(SimpleDateFormat::new)
          .collect(Collectors.toList());

  public static Long translateTime(String time) {
    for (SimpleDateFormat dateFormat : dateFormats) {
      try {
        return dateFormat.parse(time).getTime();
      } catch (ParseException e) {
        log.error("Unable to parse {}. {}", time, e.getMessage());
      }
    }
    return null;
  }
}
