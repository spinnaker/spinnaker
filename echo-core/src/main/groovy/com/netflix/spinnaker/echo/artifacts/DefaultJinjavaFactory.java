package com.netflix.spinnaker.echo.artifacts;

import com.hubspot.jinjava.Jinjava;

/**
 * Creates a regular jinjava.
 * You can overwrite this to create a custom jinjava with
 *  things like custom filters and tags.
 */
public class DefaultJinjavaFactory implements JinjavaFactory {

  @Override
  public Jinjava create() {
    return new Jinjava();
  }
}
