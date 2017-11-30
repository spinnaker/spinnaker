package com.netflix.spinnaker.clouddriver.google.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Distribution policy for selecting zones in a regional MIG.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleDistributionPolicy {
  List<String> zones;
}
