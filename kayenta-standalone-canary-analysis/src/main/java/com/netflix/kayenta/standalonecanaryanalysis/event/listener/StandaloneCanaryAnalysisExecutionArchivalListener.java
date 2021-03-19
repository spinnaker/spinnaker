package com.netflix.kayenta.standalonecanaryanalysis.event.listener;

import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.standalonecanaryanalysis.event.StandaloneCanaryAnalysisExecutionCompletedEvent;
import com.netflix.kayenta.standalonecanaryanalysis.storage.StandaloneCanaryAnalysisObjectType;
import com.netflix.kayenta.storage.StorageServiceRepository;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StandaloneCanaryAnalysisExecutionArchivalListener {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final Consumer<StandaloneCanaryAnalysisExecutionCompletedEvent> preScapeArchiveHook;

  @Autowired
  public StandaloneCanaryAnalysisExecutionArchivalListener(
      AccountCredentialsRepository accountCredentialsRepository,
      StorageServiceRepository storageServiceRepository,
      @Qualifier("pre-scape-archive-hook")
          Consumer<StandaloneCanaryAnalysisExecutionCompletedEvent> preScapeArchiveHook) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.preScapeArchiveHook = preScapeArchiveHook;
  }

  @EventListener
  public void onApplicationEvent(StandaloneCanaryAnalysisExecutionCompletedEvent event) {
    preScapeArchiveHook.accept(event);
    var response = event.getCanaryAnalysisExecutionStatusResponse();

    Optional.ofNullable(response.getStorageAccountName())
        .ifPresent(
            storageAccountName -> {
              var resolvedStorageAccountName =
                  accountCredentialsRepository
                      .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
                      .getName();

              var storageService =
                  storageServiceRepository.getRequiredOne(resolvedStorageAccountName);

              storageService.storeObject(
                  resolvedStorageAccountName,
                  StandaloneCanaryAnalysisObjectType.STANDALONE_CANARY_RESULT_ARCHIVE,
                  response.getPipelineId(),
                  response);
            });
  }
}
