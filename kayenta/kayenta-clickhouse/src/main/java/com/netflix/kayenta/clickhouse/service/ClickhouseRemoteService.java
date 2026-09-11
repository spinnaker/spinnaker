package com.netflix.kayenta.clickhouse.service;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.netflix.kayenta.clickhouse.security.ClickhouseCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Thin wrapper around the official Clickhouse Java client. Kayenta never generates SQL - every
 * query executed here comes verbatim from a canary metric's {@code customInlineTemplate} / {@code
 * customFilterTemplate}, so the only contract this class enforces on the caller is: the query must
 * return a single numeric column, with one row per expected step bucket in ascending time order.
 */
public class ClickhouseRemoteService {

  private final Client client;

  public ClickhouseRemoteService(ClickhouseCredentials credentials) {
    Client.Builder builder =
        new Client.Builder()
            .addEndpoint(credentials.getEndpointUrl())
            .setUsername(credentials.getUsername())
            .setPassword(credentials.getPassword());

    if (StringUtils.hasText(credentials.getDatabase())) {
      builder.setDefaultDatabase(credentials.getDatabase());
    }

    this.client = builder.build();
  }

  /**
   * Executes the supplied SQL and returns the first column of each returned row as a {@link
   * Double}, in row order. A SQL {@code NULL} is returned as {@link Double#NaN}.
   */
  public List<Double> queryValues(String sql) throws IOException {
    try {
      List<GenericRecord> records = client.queryAll(sql);
      List<Double> values = new ArrayList<>(records.size());

      for (GenericRecord record : records) {
        values.add(record.hasValue(1) ? record.getDouble(1) : Double.NaN);
      }

      return values;
    } catch (Exception e) {
      throw new IOException("Failed executing Clickhouse query: " + sql, e);
    }
  }
}
