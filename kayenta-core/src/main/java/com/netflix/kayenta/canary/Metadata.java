package com.netflix.kayenta.canary;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@Data
public class Metadata {
  @Getter
  @NonNull
  protected String name;

  @Getter
  @NonNull
  protected String value;

  // 'hidden' is a UI-hint to show or not show this value by default in the UI,
  // likely in a generic way.
  @Getter
  @Builder.Default
  protected Boolean hidden = false;
}
