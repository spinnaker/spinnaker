package com.netflix.spinnaker.echo.pipelinetriggers.orca;

import com.netflix.spinnaker.echo.model.Pipeline;
import lombok.Value;
import retrofit.http.Body;
import retrofit.http.POST;
import rx.Observable;

public interface OrcaService {
  @POST("/orchestrate")
  Observable<Response> trigger(@Body Pipeline pipeline);

  class Response {
    private String ref;

    public Response() {
      // do nothing
    }

    public String getRef() {
      return ref;
    }
  }
}
