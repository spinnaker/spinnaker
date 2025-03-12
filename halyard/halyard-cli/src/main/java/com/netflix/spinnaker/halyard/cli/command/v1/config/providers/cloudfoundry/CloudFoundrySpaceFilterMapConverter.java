package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.cloudfoundry;

import com.beust.jcommander.IStringConverter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CloudFoundrySpaceFilterMapConverter
    implements IStringConverter<Map<String, Set<String>>> {

  @Override
  public Map<String, Set<String>> convert(String filter) {
    Map<String, Set<String>> spaceFilters = new HashMap<>();

    String[] s = filter.split(",");
    for (String pair : s) {
      String[] p = pair.split(":");
      if (p.length > 2) {
        throw new IllegalArgumentException(
            "Syntax must be one of the following: 'Organization1' or 'Organization1:Space1' or 'Organization1:Space1,Organization2:Space2'");
      } else if (p.length == 2) {
        spaceFilters.computeIfAbsent(p[0], k -> new HashSet<>()).add(p[1]);
      } else if (p.length == 1) {
        spaceFilters.computeIfAbsent(p[0], k -> new HashSet<>());
      }
    }
    return spaceFilters;
  }
}
