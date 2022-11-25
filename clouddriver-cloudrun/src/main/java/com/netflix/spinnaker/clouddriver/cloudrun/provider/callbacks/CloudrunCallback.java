package com.netflix.spinnaker.clouddriver.cloudrun.provider.callbacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import groovy.lang.Closure;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

@Slf4j
public class CloudrunCallback<T> extends JsonBatchCallback<T> {
  public CloudrunCallback success(Closure successCb) {
    this.successCb = successCb;
    return this;
  }

  public CloudrunCallback failure(Closure failureCb) {
    this.failureCb = failureCb;
    return this;
  }

  @Override
  public void onSuccess(T response, HttpHeaders httpHeaders) throws IOException {
    getSuccessCb();
  }

  @Override
  public void onFailure(GoogleJsonError e, HttpHeaders httpHeaders) throws IOException {
    if (DefaultGroovyMethods.asBoolean(failureCb)) {
      getFailureCb();
    } else {
      String errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e);
      log.error(errorJson);
    }
  }

  public Closure getSuccessCb() {
    return successCb;
  }

  public void setSuccessCb(Closure successCb) {
    this.successCb = successCb;
  }

  public Closure getFailureCb() {
    return failureCb;
  }

  public void setFailureCb(Closure failureCb) {
    this.failureCb = failureCb;
  }

  private Closure successCb;
  private Closure failureCb;
}
