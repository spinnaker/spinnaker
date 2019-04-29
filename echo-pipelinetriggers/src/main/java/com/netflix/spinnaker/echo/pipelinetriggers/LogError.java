package com.netflix.spinnaker.echo.pipelinetriggers;

import lombok.extern.slf4j.Slf4j;
import rx.functions.Action1;

@Slf4j
public class LogError implements Action1<Throwable> {
  @Override
  public void call(Throwable throwable) {
    log.error("Observable Failed", throwable);
  }
}
