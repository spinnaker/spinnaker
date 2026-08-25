package com.netflix.kayenta.clickhouse.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class ClickhouseConfigurationProperties {

  @Getter private List<ClickhouseManagedAccount> accounts = new ArrayList<>();
}
