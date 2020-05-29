package com.netflix.spinnaker.front50.model;

import com.netflix.spinnaker.front50.exception.NotFoundException;
import java.time.Duration;
import java.util.Collection;

public interface ItemDAO<T> {
  T findById(String id) throws NotFoundException;

  Collection<T> all();

  /**
   * It can be expensive to refresh a bucket containing a large number of objects.
   *
   * @param refresh true to refresh
   * @return When {@code refresh} is false, the most recently cached set of objects will be
   *     returned. *
   */
  Collection<T> all(boolean refresh);

  Collection<T> history(String id, int maxResults);

  T create(String id, T item);

  void update(String id, T item);

  void delete(String id);

  void bulkImport(Collection<T> items);

  default void bulkDelete(Collection<String> ids) {
    for (String id : ids) {
      delete(id);
    }
  }

  boolean isHealthy();

  default long getHealthIntervalMillis() {
    return Duration.ofSeconds(30).toMillis();
  }
}
