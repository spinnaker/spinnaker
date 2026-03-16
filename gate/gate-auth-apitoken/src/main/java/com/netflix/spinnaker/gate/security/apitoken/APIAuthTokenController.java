package com.netflix.spinnaker.gate.security.apitoken;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.collections4.SetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/token")
public class APIAuthTokenController {

  @Value("${auth.apitokens.maxValidInSeconds:604800}") // 7 days by default
  private long maxValidityInSeconds;

  @Autowired private APITokenHelper apiTokenHelper;

  /*
   * At least some of this is pulled from:
   * https://github.com/murraco/spring-boot-jwt/blob/master/src/main/java/murraco/security/JwtTokenProvider.java
   */
  @RequestMapping(method = RequestMethod.POST)
  public String createToken(Integer validityInSeconds, Set<String> roles)
      throws JsonProcessingException {
    /*
    Translates user info from the logged in user into a ApiTokenAccount object for storage in redis.  We
     then use the API Token that's randomly generated as the redis key PLUS the subject for the principal
     this lets us load information from redis, while the JWT only contains a pointer to the redis entry.
     this lets us ALSO destroy the redis entry there-by disabling the API token.
     E.g. this lets us do revocation lists!  ONE gotcha - this is ALWAYS generating a token.  It likely should
     check and only generate if one doesn't exist, or is expired or do something more intelligent.
     */
    UserDetails userInfo =
        (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    Set<String> userRoles = AuthorityUtils.authorityListToSet(userInfo.getAuthorities());

    ApiTokenAccount apiTokenAccount =
        ApiTokenAccount.builder()
            .username(
                userInfo.getUsername()
                    + "-apitokenaccount") // Add apitokenaccount so we KNOW this is an API based
            // operation
            // Default to all the user roles, or allow a selected set for the user.  Limit to the
            // roles available from the user roles
            // to prevent privlege escalation
            .roles(ObjectUtils.isEmpty(roles) ? userRoles : SetUtils.intersection(roles, userRoles))
            .apiToken(UUID.randomUUID().toString().toUpperCase())
            .build();

    return apiTokenHelper.createTokenAccount(
        apiTokenAccount,
        Date.from(
            Instant.now()
                .plus(Duration.ofSeconds(Math.min(maxValidityInSeconds, validityInSeconds)))));
  }

  @RequestMapping(method = RequestMethod.DELETE)
  public void deleteToken(@RequestParam String apiToken) {
    // Gate normally stores users as a UserDetails object.  We extract that to do some validation.
    UserDetails userInfo =
        (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    ApiTokenAccount apiTokenAccount = apiTokenHelper.fetchAccount(apiToken);
    // ONLY delete if user is the same as who created it OR the user is a "super-admin"
    if (apiTokenAccount.getUsername().startsWith(userInfo.getUsername())) {
      apiTokenHelper.deleteAccount(apiToken);
    }
  }

  // RESTRICT TO ADMINS... or figure out another method on this...
  @RequestMapping(method = RequestMethod.GET)
  public Set<ApiTokenAccount> getTokens() {
    // Return either a list of the user tokens OR all available tokens if an "admin"
    return apiTokenHelper.fetchTokens();
  }
}
