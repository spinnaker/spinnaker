/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.kayenta.canary.providers.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class QueryConfigUtils {

  @VisibleForTesting
  public static String expandCustomFilter(CanaryConfig canaryConfig,
                                          CanaryMetricSetQueryConfig metricSetQuery,
                                          CanaryScope canaryScope,
                                          String[] baseScopeAttributes) throws IOException {
    if (metricSetQuery.getCustomFilter() != null) {
      throw new IllegalArgumentException("CanaryMetricSetQueryConfig.customFilter is deprecated, use CanaryMetricSetQueryConfig.customInlineTemplate instead.");
    }

    String customInlineTemplate = metricSetQuery.getCustomInlineTemplate();
    String customFilterTemplate = metricSetQuery.getCustomFilterTemplate();
    String templateToExpand;
    String expandedTemplate;

    log.debug("customInlineTemplate={}", customInlineTemplate);
    log.debug("customFilterTemplate={}", customFilterTemplate);

    if (StringUtils.isEmpty(customInlineTemplate) && StringUtils.isEmpty(customFilterTemplate)) {
      return null;
    }

    if (!StringUtils.isEmpty(customInlineTemplate)) {
      templateToExpand = unescapeTemplate(customInlineTemplate);
    } else {
      Map<String, String> templates = canaryConfig.getTemplates();

      // TODO(duftler): Handle this as a config validation step instead.
      if (CollectionUtils.isEmpty(templates)) {
        throw new IllegalArgumentException("Custom filter template '" + customFilterTemplate + "' was referenced, " +
                                           "but no templates were defined.");
      } else if (!templates.containsKey(customFilterTemplate)) {
        throw new IllegalArgumentException("Custom filter template '" + customFilterTemplate + "' was not found.");
      }
      templateToExpand = unescapeTemplate(templates.get(customFilterTemplate));
    }

    Configuration configuration = new Configuration(Configuration.VERSION_2_3_26);
    Template template = new Template("template", new StringReader(templateToExpand), configuration);

    try {
      log.debug("extendedScopeParams={}", canaryScope.getExtendedScopeParams());

      Map<String, String> templateBindings = new LinkedHashMap<>();
      populateTemplateBindings(canaryScope, baseScopeAttributes, templateBindings, false);
      populateTemplateBindings(metricSetQuery, baseScopeAttributes, templateBindings, true);

      if (!CollectionUtils.isEmpty(canaryScope.getExtendedScopeParams())) {
        templateBindings.putAll(canaryScope.getExtendedScopeParams());
      }

      log.debug("templateBindings={}", templateBindings);

      expandedTemplate = FreeMarkerTemplateUtils.processTemplateIntoString(template, templateBindings);
    } catch (TemplateException e) {
      throw new IllegalArgumentException("Problem evaluating custom filter template:", e);
    }

    log.debug("expandedTemplate={}", expandedTemplate);

    return expandedTemplate;
  }

  private static void populateTemplateBindings(Object bean,
                                               String[] baseScopeAttributes,
                                               Map<String, String> templateBindings,
                                               boolean lenient) {
    BeanInfo beanInfo;

    try {
      beanInfo = Introspector.getBeanInfo(bean.getClass());
    } catch (IntrospectionException e) {
      throw new IllegalArgumentException(e);
    }

    PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();

    for (String baseScopeAttribute : baseScopeAttributes) {
      try {
        Optional<PropertyDescriptor> propertyDescriptor = Stream.of(propertyDescriptors)
          .filter(p -> p.getName().equals(baseScopeAttribute))
          .findFirst();

        if (!propertyDescriptor.isPresent()) {
          if (lenient) {
            continue;
          } else {
            throw new IllegalArgumentException("Unable to find property '" + baseScopeAttribute + "'.");
          }
        }

        String propertyValue = (String)propertyDescriptor.get().getReadMethod().invoke(bean);

        if (!StringUtils.isEmpty(propertyValue)) {
          templateBindings.put(baseScopeAttribute, propertyValue);
        }
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  @VisibleForTesting
  public static CanaryConfig escapeTemplates(CanaryConfig canaryConfig) {
    if (!CollectionUtils.isEmpty(canaryConfig.getTemplates())) {
      Map<String, String> escapedTemplates =
        canaryConfig.getTemplates()
          .entrySet()
          .stream()
          .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().replace("${", "$\\{")));

      canaryConfig = canaryConfig.toBuilder().templates(escapedTemplates).build();
    }

    return canaryConfig;
  }

  @VisibleForTesting
  public static String unescapeTemplate(String template) {
    return template.replace("$\\{", "${");
  }
}
