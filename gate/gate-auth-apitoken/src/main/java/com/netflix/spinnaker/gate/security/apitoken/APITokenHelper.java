package com.netflix.spinnaker.gate.security.apitoken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Log
@Component
public class APITokenHelper {

  public static final String REDIS_PATH = "api-tokens:";
  private final SecretKey secretKey;
  private final JedisPool pool;
  private final ObjectMapper objectMapper;

  public APITokenHelper(
      @Value("${auth.apitokens.secretKey}") String secretKeyString,
      JedisPool pool,
      ObjectMapper objectMapper) {
    this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    this.pool = pool;
    this.objectMapper = objectMapper;
  }

  public String createTokenAccount(ApiTokenAccount apiTokenAccount, Date expirationDate)
      throws JsonProcessingException {
    Jedis jedis = pool.getResource();
    jedis.set(
        (APITokenHelper.REDIS_PATH + apiTokenAccount.getApiToken()),
        objectMapper.writeValueAsString(apiTokenAccount));
    jedis.close();

    Claims claim =
        Jwts.claims()
            .issuedAt(Date.from(Instant.now()))
            .expiration(expirationDate)
            .subject(apiTokenAccount.getApiToken())
            .build();
    claim.put("userId", apiTokenAccount.getUsername());
    claim.put("roles", apiTokenAccount.getRoles());
    return Jwts.builder() //
        .claims(claim) //
        .issuedAt(Date.from(Instant.now())) //
        .expiration(expirationDate) //
        .signWith(secretKey, Jwts.SIG.HS512) //
        .compact();
  }

  public void deleteAccount(String apiToken) {
    Jedis jedis = pool.getResource();
    jedis.del(REDIS_PATH + apiToken);
    jedis.close();
  }

  public ApiTokenAccount fetchAccount(String apiToken) throws AuthenticationException {
    Claims claim =
        Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(apiToken).getPayload();
    Jedis jedis = pool.getResource();
    String apiTokenFromRedis = jedis.get((REDIS_PATH + claim.getSubject()));
    jedis.close();
    if (apiTokenFromRedis == null) {
      log.warning(
          String.format(
              "No authentication found for api token subject %s which means this was either revoked... OR it's a standard oauth token from another source",
              claim.getSubject()));
      throw new AuthenticationCredentialsNotFoundException("Token has been revoked or invalid");
    }
    return objectMapper.convertValue(apiTokenFromRedis, ApiTokenAccount.class);
  }

  // Either limit to admins (and even THEN considered dangerous... as this shows ALL TOKENS that
  // would work
  // allowing impersonation of any user...
  // OR switch to masking the token and showing like the first 4 characters, then account info &
  // roles.
  public Set<ApiTokenAccount> fetchTokens() {
    return pool.getResource().keys(REDIS_PATH + "*").stream()
        .map(this::fetchAccount)
        .collect(Collectors.toSet());
  }
}
