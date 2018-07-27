package com.netflix.spinnaker.echo.artifacts;

import com.hubspot.jinjava.Jinjava;

public interface JinjavaFactory {

  Jinjava create();
}
