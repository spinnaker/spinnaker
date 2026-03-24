package com.netflix.spinnaker.gate.security.apitoken;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ApiTokenAccount {

  @EqualsAndHashCode.Include private String username;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @EqualsAndHashCode.Include
  private String apiToken;

  private Set<String> roles;
  private long expirationTimeUtc;

  public String toString() {
    // ONLY Print out the first four characters of the apiToken JUST in case something does a
    // toSTring
    return username + ":" + apiToken.substring(0, 4) + "....";
  }
}
