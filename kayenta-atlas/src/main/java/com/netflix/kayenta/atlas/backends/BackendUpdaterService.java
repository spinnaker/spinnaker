package com.netflix.kayenta.atlas.backends;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.atlas.enabled")
public class BackendUpdaterService extends AbstractHealthIndicator {
  private final RetrofitClientFactory retrofitClientFactory;
  private final ObjectMapper objectMapper;
  private final OkHttpClient okHttpClient;
  private final List<BackendUpdater> backendUpdaters = new ArrayList<>();
  private int checksCompleted = 0;

  @Autowired
  public BackendUpdaterService(RetrofitClientFactory retrofitClientFactory, ObjectMapper objectMapper, OkHttpClient okHttpClient) {
    this.retrofitClientFactory = retrofitClientFactory;
    this.objectMapper = objectMapper;
    this.okHttpClient = okHttpClient;
  }

  @Scheduled(initialDelay = 2000, fixedDelay=122000)
  public synchronized void run() {
    // TODO: this will fetch the same uri even if they share the same URI.
    // TODO: It also has locking issues, in that we could hold a lock for a long time.
    // TODO: Locking may not matter as we should rarely, if ever, modify this list.
    // TODO: Although, for healthcheck, it may...
    int checks = 0;
    for (BackendUpdater updater: backendUpdaters) {
      Boolean result = updater.run(retrofitClientFactory, objectMapper, okHttpClient);
      if (result)
        checks++;
    }
    checksCompleted = checks;
  }

  public synchronized void add(BackendUpdater updater) {
    backendUpdaters.add(updater);
  }

  @Override
  protected synchronized void doHealthCheck(Health.Builder builder) throws Exception {
    if (checksCompleted == backendUpdaters.size()) {
      builder.up();
    } else {
      builder.down();
    }
    builder.withDetail("checksCompleted", checksCompleted);
    builder.withDetail("checksExpected", backendUpdaters.size());
  }
}
