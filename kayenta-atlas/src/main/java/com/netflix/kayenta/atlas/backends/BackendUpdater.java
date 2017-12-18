package com.netflix.kayenta.atlas.backends;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.model.Backend;
import com.netflix.kayenta.atlas.service.BackendsRemoteService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.squareup.okhttp.OkHttpClient;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import retrofit.RetrofitError;
import retrofit.converter.JacksonConverter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@Builder
public class BackendUpdater {
  @Getter
  private final BackendDatabase backendDatabase = new BackendDatabase();

  @NotNull
  private String uri;

  // If we have retrieved backends.json at least once, we will keep using it forever
  // even if we fail later.  It doesn't really change much over time, so this
  // is likely safe enough.
  private boolean succeededAtLeastOnce = false;

  boolean run(RetrofitClientFactory retrofitClientFactory, ObjectMapper objectMapper, OkHttpClient okHttpClient) {
    RemoteService remoteService = new RemoteService();
    remoteService.setBaseUrl(uri);
    BackendsRemoteService backendsRemoteService = retrofitClientFactory.createClient(BackendsRemoteService.class,
                                                                                     new JacksonConverter(objectMapper),
                                                                                     remoteService,
                                                                                     okHttpClient);
    try {
      List<Backend> backends = backendsRemoteService.fetch();
      backendDatabase.update(backends);
    } catch (RetrofitError e) {
      log.warn("While fetching atlas backends from " + uri, e);
      return succeededAtLeastOnce;
    }
    succeededAtLeastOnce = true;
    return true;
  }
}
