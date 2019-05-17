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
package com.netflix.spinnaker.front50.redis;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.util.Assert;

public class RedisPipelineTemplateDAO implements PipelineTemplateDAO {

  static final String BOOK_KEEPING_KEY = "com.netflix.spinnaker:front50:pipelineTemplates";

  RedisTemplate<String, PipelineTemplate> redisTemplate;

  @Override
  public Collection<PipelineTemplate> getPipelineTemplatesByScope(List<String> scope) {
    return all().stream().filter(it -> it.containsAnyScope(scope)).collect(Collectors.toList());
  }

  @Override
  public PipelineTemplate findById(String id) throws NotFoundException {
    PipelineTemplate pipelineTemplate =
        (PipelineTemplate) redisTemplate.opsForHash().get(BOOK_KEEPING_KEY, id);
    if (pipelineTemplate == null) {
      throw new NotFoundException("No pipeline template found with id '" + id + "'");
    }
    return pipelineTemplate;
  }

  @Override
  public Collection<PipelineTemplate> all() {
    return all(true);
  }

  @Override
  public Collection<PipelineTemplate> all(boolean refresh) {
    try (Cursor<Map.Entry<Object, Object>> c =
        redisTemplate
            .opsForHash()
            .scan(BOOK_KEEPING_KEY, ScanOptions.scanOptions().match("*").build())) {
      return StreamSupport.stream(Spliterators.spliteratorUnknownSize(c, 0), false)
          .map(e -> (PipelineTemplate) e.getValue())
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<PipelineTemplate> history(String id, int maxResults) {
    return Lists.newArrayList(findById(id));
  }

  @Override
  public PipelineTemplate create(String id, PipelineTemplate item) {
    Assert.notNull(item.getId(), "id field must NOT to be null!");
    Assert.notEmpty(item.getScopes(), "scope field must have at least ONE scope!");

    redisTemplate.opsForHash().put(BOOK_KEEPING_KEY, item.getId(), item);

    return item;
  }

  @Override
  public void update(String id, PipelineTemplate item) {
    item.setLastModified(System.currentTimeMillis());
    create(id, item);
  }

  @Override
  public void delete(String id) {
    redisTemplate.opsForHash().delete(BOOK_KEEPING_KEY, id);
  }

  @Override
  public void bulkImport(Collection<PipelineTemplate> items) {
    items.forEach(it -> create(it.getId(), it));
  }

  @Override
  public boolean isHealthy() {
    try {
      redisTemplate.opsForHash().get("", "");
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
