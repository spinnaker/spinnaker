/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.controller;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServiceAccount;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexServiceAccountProvider;
import groovy.util.logging.Slf4j;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/yandex/serviceAcounts")
public class YandexServiceAccountController {
  private final YandexServiceAccountProvider yandexServiceAccountProvider;

  @Autowired
  private YandexServiceAccountController(
      YandexServiceAccountProvider yandexServiceAccountProvider) {
    this.yandexServiceAccountProvider = yandexServiceAccountProvider;
  }

  @RequestMapping(value = "/{account}", method = RequestMethod.GET)
  public List<YandexCloudServiceAccount> list(@PathVariable String account) {
    return (Strings.isNullOrEmpty(account)
            ? yandexServiceAccountProvider.getAll()
            : yandexServiceAccountProvider.findByAccount(account))
        .stream()
            .sorted(Comparator.comparing(YandexCloudServiceAccount::getName))
            .collect(Collectors.toList());
  }
}
