package com.netflix.spinnaker.orca.locks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.Objects;
import java.util.Optional;

public class LockContext {
  public static class LockContextBuilder {

    public static class LockValueBuilder {
      private String application;
      private String type;
      private String id;
      private Stage stage;

      LockValueBuilder() {
        this(null, null, null, null);
      }

      @JsonCreator
      public LockValueBuilder(@JsonProperty("application") String application,
                              @JsonProperty("type") String type,
                              @JsonProperty("id") String id) {
        this(application, type, id, null);
      }

      LockValueBuilder(String application, String type, String id, Stage stage) {
        this.application = application;
        this.type = type;
        this.id = id;
        this.stage = stage;
      }

      public LockValueBuilder withStage(Stage stage) {
        this.stage = stage;
        return this;
      }

      public LockManager.LockValue build() {
        final Optional<Execution> execution = Optional.ofNullable(stage).map(Stage::getExecution);

        final String application = Optional.ofNullable(this.application).orElseGet(() ->
          execution
            .map(Execution::getApplication)
            .orElse(null));

        final String type = Optional.ofNullable(this.type).orElseGet(() ->
          execution
            .map(Execution::getType)
            .map(Execution.ExecutionType::toString)
            .orElse(null));

        //TODO(cfieber): lockValue.id:
        // if we are a child pipeline / strategy we probably need to use the parent execution id
        final String id = Optional.ofNullable(this.id).orElseGet(() ->
          execution
            .map(Execution::getId)
            .orElse(null));

        return new LockManager.LockValue(application, type, id);
      }
    }


    private String lockName;
    private LockValueBuilder lockValue;
    private String lockHolder;
    private Stage stage;

    public LockContextBuilder() {
      this(null, null, null, null);
    }

    @JsonCreator
    public LockContextBuilder(
      @JsonProperty("lockName") String lockName,
      @JsonProperty("lockValue") LockValueBuilder lockValue,
      @JsonProperty("lockHolder") String lockHolder) {
      this(lockName, lockValue, lockHolder, null);
    }

    LockContextBuilder(String lockName, LockValueBuilder lockValue, String lockHolder, Stage stage) {
      this.lockName = lockName;
      this.lockValue = lockValue;
      this.lockHolder = lockHolder;
      this.stage = stage;
    }

    public LockContextBuilder withStage(Stage stage) {
      this.stage = stage;
      Optional.ofNullable(lockValue).ifPresent(lv -> lv.withStage(stage));
      return this;
    }

    public LockContext build() {
      final LockManager.LockValue lockValue =
        Optional.ofNullable(this.lockValue)
          .orElseGet(() -> new LockValueBuilder().withStage(stage))
          .build();

      final String lockHolder = Optional.ofNullable(this.lockHolder).orElseGet(() ->
        Optional.ofNullable(stage)
          .map(Stage::getId)
          .orElse(null));

      return new LockContext(lockName, lockValue, lockHolder);
    }
  }

  private final String lockName;
  private final LockManager.LockValue lockValue;
  private final String lockHolder;

  LockContext(String lockName,
              LockManager.LockValue lockValue,
              String lockHolder) {
    this.lockName = Objects.requireNonNull(lockName);
    this.lockValue = Objects.requireNonNull(lockValue);
    this.lockHolder = Objects.requireNonNull(lockHolder);
  }

  public String getLockName() {
    return lockName;
  }

  public LockManager.LockValue getLockValue() {
    return lockValue;
  }

  public String getLockHolder() {
    return lockHolder;
  }
}
