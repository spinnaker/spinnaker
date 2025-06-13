package com.netflix.spinnaker.clouddriver.titus.caching.utils;

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CachingSchemaUtil {

  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AwsLookupUtil awsLookupUtil;
  private final Map<String, CachingSchema> cachingSchemaForAccounts = new LinkedHashMap<>();

  @Autowired
  public CachingSchemaUtil(
      AccountCredentialsProvider accountCredentialsProvider, AwsLookupUtil awsLookupUtil) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.awsLookupUtil = awsLookupUtil;
  }

  public CachingSchema getCachingSchemaForAccount(String account) {
    init();
    return Optional.ofNullable(cachingSchemaForAccounts.get(account)).orElse(CachingSchema.V2);
  }

  @PostConstruct
  private void init() {
    accountCredentialsProvider.getAll().stream()
        .filter(c -> c instanceof NetflixTitusCredentials)
        .forEach(
            c -> {
              NetflixTitusCredentials credentials = (NetflixTitusCredentials) c;

              Collection<TitusRegion> regions = credentials.getRegions();
              regions.forEach(
                  region -> {
                    cachingSchemaForAccounts.put(
                        credentials.getName(), cachingSchemaFor(credentials));
                    cachingSchemaForAccounts.put(
                        awsLookupUtil.awsAccountId(credentials.getName(), region.getName()),
                        cachingSchemaFor(credentials));
                  });
            });
  }

  private static CachingSchema cachingSchemaFor(NetflixTitusCredentials credentials) {
    return CachingSchema.V2;
  }
}
