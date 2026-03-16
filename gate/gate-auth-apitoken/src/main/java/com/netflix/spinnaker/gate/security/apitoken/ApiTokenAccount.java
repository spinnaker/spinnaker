package com.netflix.spinnaker.gate.security.apitoken;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ApiTokenAccount {

  private String username;
  private String apiToken;
  private Set<String> roles;
  private long expirationTimeUtc;
}
