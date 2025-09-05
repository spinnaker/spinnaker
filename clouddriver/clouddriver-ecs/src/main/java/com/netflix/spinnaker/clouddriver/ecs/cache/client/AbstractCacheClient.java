/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractCacheClient<T> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final String keyNamespace;
  protected final Cache cacheView;

  /**
   * @param cacheView The Cache that the client will query.
   * @param keyNamespace The key namespace that the client is responsible for.
   */
  AbstractCacheClient(Cache cacheView, String keyNamespace) {
    this.cacheView = cacheView;
    this.keyNamespace = keyNamespace;
  }

  /**
   * @param cacheData CacheData that will be converted into an object.
   * @return An object of the generic type.
   */
  protected abstract T convert(CacheData cacheData);

  /**
   * @return A list of all generic type objects belonging to the key namespace.
   */
  public Collection<T> getAll() {
    Collection<CacheData> allData = cacheView.getAll(keyNamespace);
    return convertAll(allData);
  }

  /**
   * @param account name of the AWS account, as defined in clouddriver.yml
   * @param region region of the AWS account, as defined in clouddriver.yml
   * @return A list of all generic type objects belonging to the account and region in the key
   *     namespace.
   */
  public Collection<T> getAll(String account, String region) {
    Collection<CacheData> data = fetchFromCache(account, region);
    return convertAll(data);
  }

  public Collection<T> getAll(Collection<String> identifiers) {
    Collection<CacheData> allData = cacheView.getAll(keyNamespace, identifiers);
    if (allData == null) {
      return Collections.emptyList();
    }
    return convertAll(allData);
  }

  /**
   * @param key A key within the key namespace that will be used to retrieve the object.
   * @return An object of the generic type that is associated to the key.
   */
  public T get(String key) {
    CacheData cacheData = cacheView.get(keyNamespace, key);
    if (cacheData != null) {
      return convert(cacheData);
    }
    return null;
  }

  /**
   * @param cacheData A collection of CacheData that will be converted into a collection of generic
   *     typ objects.
   * @return A collection of generic typ objects.
   */
  private Collection<T> convertAll(Collection<CacheData> cacheData) {
    return cacheData.stream().map(this::convert).collect(Collectors.toList());
  }

  /**
   * @param account name of the AWS account, as defined in clouddriver.yml
   * @param region region of the AWS account, as defined in clouddriver.yml
   * @return
   */
  private Collection<CacheData> fetchFromCache(String account, String region) {
    log.debug("fetching all for account '{}' and region '{}'", account, region);
    String accountFilter = account != null ? account + Keys.SEPARATOR : "*" + Keys.SEPARATOR;
    String regionFilter = region != null ? region + Keys.SEPARATOR : "*" + Keys.SEPARATOR;
    Set<String> keys = new HashSet<>();
    String pattern =
        "ecs" + Keys.SEPARATOR + keyNamespace + Keys.SEPARATOR + accountFilter + regionFilter + "*";
    Collection<String> nameMatches = cacheView.filterIdentifiers(keyNamespace, pattern);

    keys.addAll(nameMatches);

    Collection<CacheData> allData = cacheView.getAll(keyNamespace, keys);

    if (allData == null) {
      return Collections.emptyList();
    }

    return allData;
  }

  public Collection<String> filterIdentifiers(String glob) {
    return cacheView.filterIdentifiers(keyNamespace, glob);
  }
}
