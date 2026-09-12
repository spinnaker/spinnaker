/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.haproxy.client;

import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.ConfigurationApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.TransactionsApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Transaction;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Runs Data Plane API configuration changes inside a transaction: fetch the configuration version,
 * start a transaction against it, apply the changes, then commit (with {@code force_reload}). A
 * commit rejected because the configuration version moved underneath us (HTTP 406/409) is retried
 * from a fresh version.
 */
public final class HaProxyTransactions {

  private static final Logger log = LoggerFactory.getLogger(HaProxyTransactions.class);
  private static final int MAX_ATTEMPTS = 3;

  private HaProxyTransactions() {}

  /** Configuration changes to apply within the transaction. */
  public interface TransactionWork {
    void apply(String transactionId) throws IOException;
  }

  public static void run(HaProxyNamedAccountCredentials credentials, TransactionWork work)
      throws IOException {
    ConfigurationApi configurationApi = credentials.getApi(ConfigurationApi.class);
    TransactionsApi transactionsApi = credentials.getApi(TransactionsApi.class);

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      Integer version = execute(configurationApi.getConfigurationVersion(null));
      Transaction transaction = execute(transactionsApi.startTransaction(version));

      try {
        work.apply(transaction.getId());
      } catch (Exception e) {
        abandon(transactionsApi, transaction.getId());
        throw e;
      }

      Response<Transaction> commit =
          transactionsApi.commitTransaction(transaction.getId(), true).execute();
      if (commit.isSuccessful()) {
        return;
      }
      if (commit.code() == 406 || commit.code() == 409) {
        log.warn(
            "Transaction {} commit conflicted (HTTP {}), retrying from a fresh version (attempt {}/{})",
            transaction.getId(),
            commit.code(),
            attempt,
            MAX_ATTEMPTS);
        abandon(transactionsApi, transaction.getId());
        continue;
      }
      throw new IllegalStateException(
          String.format(
              "Committing transaction %s failed: HTTP %d %s",
              transaction.getId(), commit.code(), errorBody(commit)));
    }
    throw new IllegalStateException(
        "Configuration version kept changing; gave up after " + MAX_ATTEMPTS + " attempts");
  }

  /** Executes the call, returning the body or throwing with the error detail. */
  public static <T> T execute(Call<T> call) throws IOException {
    Response<T> response = call.execute();
    if (!response.isSuccessful()) {
      throw new IllegalStateException(
          String.format(
              "%s %s failed: HTTP %d %s",
              call.request().method(),
              call.request().url().encodedPath(),
              response.code(),
              errorBody(response)));
    }
    return response.body();
  }

  private static void abandon(TransactionsApi transactionsApi, String transactionId) {
    try {
      transactionsApi.deleteTransaction(transactionId).execute();
    } catch (IOException e) {
      log.warn("Failed to delete abandoned transaction {}", transactionId, e);
    }
  }

  private static String errorBody(Response<?> response) {
    try {
      return response.errorBody() != null ? response.errorBody().string() : "";
    } catch (IOException e) {
      return "";
    }
  }
}
