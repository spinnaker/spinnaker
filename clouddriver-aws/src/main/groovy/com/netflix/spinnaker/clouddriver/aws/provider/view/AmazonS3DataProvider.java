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

package com.netflix.spinnaker.clouddriver.aws.provider.view;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.model.DataProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3StaticDataProviderConfiguration.*;

@Component
public class AmazonS3DataProvider implements DataProvider {
  private final ObjectMapper objectMapper;
  private final AmazonClientProvider amazonClientProvider;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final AmazonS3StaticDataProviderConfiguration configuration;

  private final Set<String> supportedIdentifiers;

  private final LoadingCache<String, Object> staticCache = CacheBuilder.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .recordStats()
    .build(
      new CacheLoader<String, Object>() {
        public Object load(String id) throws IOException {
          StaticRecord record = configuration.getStaticRecord(id);
          S3Object s3Object = fetchObject(
            record.getBucketAccount(), record.getBucketRegion(), record.getBucketName(), record.getBucketKey()
          );

          switch (record.getType()) {
            case list:
              return objectMapper.readValue(s3Object.getObjectContent(), List.class);
            case object:
              return objectMapper.readValue(s3Object.getObjectContent(), Map.class);
          }

          return IOUtils.toString(s3Object.getObjectContent());
        }
      });

  @Autowired
  public AmazonS3DataProvider(@Qualifier("amazonObjectMapper") ObjectMapper objectMapper,
                              AmazonClientProvider amazonClientProvider,
                              AccountCredentialsRepository accountCredentialsRepository,
                              AmazonS3StaticDataProviderConfiguration configuration) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.configuration = configuration;

    this.supportedIdentifiers = configuration.getStaticRecords()
      .stream()
      .map(r -> r.getId().toLowerCase())
      .collect(Collectors.toSet());
  }

  @Override
  public Object getStaticData(String id, Map<String, Object> filters) {
    try {
      Object contents = staticCache.get(id);
      if (filters.isEmpty() || !(contents instanceof List)) {
        return contents;
      }

      return ((List<Map>) contents)
        .stream()
        .filter(r -> {
          // currently only support filtering against first level attributes (TBD whether this is even necessary)
          return filters.entrySet()
            .stream()
            .anyMatch(f -> r.get(f.getKey()).equals(f.getValue()));
        })
        .collect(Collectors.toList());
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void getAdhocData(String groupId, String bucketId, String objectId, OutputStream outputStream) {
    String[] bucketCoordinates = bucketId.split(":");
    if (bucketCoordinates.length != 3) {
      throw new IllegalArgumentException("'bucketId' must be of the form {account}:{region}:{name}");
    }

    String bucketAccount = getAccountName(bucketCoordinates[0]);
    String bucketRegion = bucketCoordinates[1];
    String bucketName = bucketCoordinates[2];

    AdhocRecord record = configuration.getAdhocRecord(groupId);
    Matcher bucketNameMatcher = record.getBucketNamePattern().matcher(bucketName);
    Matcher objectKeyMatcher = record.getObjectKeyPattern().matcher(objectId);

    if (!bucketNameMatcher.matches() || !objectKeyMatcher.matches()) {
      throw new AccessDeniedException("Access denied (bucket: " + bucketName + ", object: " + objectId + ")");
    }

    try {
      S3Object s3Object = fetchObject(bucketAccount, bucketRegion, bucketName, objectId);
      IOUtils.copy(s3Object.getObjectContent(), outputStream);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String getAccountForIdentifier(IdentifierType identifierType, String id) {
    switch (identifierType) {
      case Static:
        return configuration.getStaticRecord(id).getBucketAccount();
      case Adhoc:
        return getAccountName(id.split(":")[0]);
    }

    throw new IllegalArgumentException("Unsupported identifierType (" + identifierType + ")");
  }

  @Override
  public boolean supportsIdentifier(IdentifierType identifierType, String id) {
    switch (identifierType) {
      case Static:
        return supportedIdentifiers.contains(id.toLowerCase());
      case Adhoc:
        return configuration.getAdhocRecords()
          .stream()
          .anyMatch(r -> r.getId().equalsIgnoreCase(id));
    }

    throw new IllegalArgumentException("Unsupported identifierType (" + identifierType + ")");
  }

  CacheStats getStaticCacheStats() {
    return staticCache.stats();
  }

  protected S3Object fetchObject(String bucketAccount, String bucketRegion, String bucketName, String objectId) {
    NetflixAmazonCredentials account = (NetflixAmazonCredentials) accountCredentialsRepository.getOne(bucketAccount);

    AmazonS3 amazonS3 = amazonClientProvider.getAmazonS3(account, bucketRegion);
    return amazonS3.getObject(bucketName, objectId);
  }

  private String getAccountName(String accountIdOrName) {
    return accountCredentialsRepository.getAll()
      .stream()
      .filter(c -> accountIdOrName.equalsIgnoreCase(c.getAccountId()) || accountIdOrName.equalsIgnoreCase(c.getName()))
      .map(AccountCredentials::getName)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unsupported account identifier (accountId: " + accountIdOrName + ")"));
  }
}
