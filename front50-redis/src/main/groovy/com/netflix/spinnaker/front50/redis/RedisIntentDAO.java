package com.netflix.spinnaker.front50.redis;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.intent.Intent;
import com.netflix.spinnaker.front50.model.intent.IntentDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

public class RedisIntentDAO implements IntentDAO {

  static final String BOOK_KEEPING_KEY = "com.netflix.spinnaker:front50:intents";

  RedisTemplate<String, Intent> redisTemplate;

  @Override
  public Collection<Intent> getIntentsByKind(List<String> kind) {
    return all()
      .stream()
      .filter(it -> kind.contains(it.getKind()))
      .collect(Collectors.toList());
  }

  @Override
  public Collection<Intent> getIntentsByStatus(List<String> status) {
    return all()
      .stream()
      .filter(it -> status.contains(it.getKind()))
      .collect(Collectors.toList());
  }

  @Override
  public Intent findById(final String id) throws NotFoundException {
    Object results = redisTemplate.opsForHash().get(BOOK_KEEPING_KEY, id);
    if (!DefaultGroovyMethods.asBoolean(results)) {
      throw new NotFoundException("No intent found with id \'" + id + "\'");
    }

    return ((Intent) (results));
  }

  @Override
  public Collection<Intent> all() {
    return all(true);
  }

  @Override
  public Collection<Intent> all(boolean refresh) {
    return Lists.newArrayList(redisTemplate.opsForHash().scan(BOOK_KEEPING_KEY, ScanOptions.scanOptions().match("*").build()))
      .stream()
      .map(e -> (Intent) e.getValue())
      .collect(Collectors.toList());
  }

  @Override
  public Collection<Intent> history(String id, int maxResults) {
    return Lists.newArrayList(findById(id));
  }

  @Override
  public Intent create(String id, Intent item) {
    Assert.notNull(item.getId(), "id field must NOT to be null!");
    Assert.notNull(item.getKind(), "kind field must NOT to be null!");
    Assert.notNull(item.getSpec(), "spec field must NOT to be null!");
    Assert.notNull(item.getSchema(), "schema field must NOT to be null!");
    if (item.getStatus() == null) {
      item.setStatus("ACTIVE");
    }

    redisTemplate.opsForHash().put(BOOK_KEEPING_KEY, item.getId(), item);

    return item;
  }

  @Override
  public void update(String id, Intent item) {
    item.setLastModified(System.currentTimeMillis());
    create(id, item);
  }

  @Override
  public void delete(String id) {
    redisTemplate.opsForHash().delete(BOOK_KEEPING_KEY, id);
  }

  @Override
  public void bulkImport(Collection<Intent> items) {
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
