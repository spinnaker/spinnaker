/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth

import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.DockerUserAgent
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.exception.DockerRegistryAuthenticationException
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

import java.nio.charset.StandardCharsets

@Slf4j
class DockerBearerTokenService {
  Map<String, TokenService> realmToService
  Map<String, DockerBearerToken> cachedTokens
  String username
  String password
  String passwordCommand
  File passwordFile
  String authWarning
  ServiceClientProvider serviceClientProvider

  final static String userAgent = DockerUserAgent.getUserAgent()

  DockerBearerTokenService(ServiceClientProvider serviceClientProvider) {
    realmToService = new HashMap<String, TokenService>()
    cachedTokens = new HashMap<String, DockerBearerToken>()
    this.serviceClientProvider = serviceClientProvider
  }

  DockerBearerTokenService(String username, String password, String passwordCommand, ServiceClientProvider serviceClientProvider) {
    this(serviceClientProvider)
    this.username = username
    this.password = password
    this.passwordCommand = passwordCommand
  }

  DockerBearerTokenService(String username, File passwordFile, ServiceClientProvider serviceClientProvider) {
    this(serviceClientProvider)
    this.username = username
    this.passwordFile = passwordFile
  }

  String getBasicAuth() {
    if (!(username || password || passwordCommand || passwordFile)) {
      return null
    }

    def resolvedPassword = null

    if (password) {
      resolvedPassword = password
    } else if (passwordCommand) {
      def pb = new ProcessBuilder("bash", "-c", passwordCommand)
      def process = pb.start()
      def errCode = process.waitFor()
      log.debug("Full command is: ${pb.command()}")
      if (errCode != 0) {
        def err = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8)
        log.error("Password command returned a non 0 return code, stderr/stdout was: '${err}'")
      }
      resolvedPassword = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim()
      log.debug("resolvedPassword is ${resolvedPassword}")
    } else if (passwordFile) {
      resolvedPassword = new BufferedReader(new FileReader(passwordFile)).getText()
    } else {
      resolvedPassword = "" // I'm assuming it's ok to have an empty password if the username is specified
    }
    if (resolvedPassword?.length() > 0) {
      def message = "Your registry password has %s whitespace, if this is unintentional authentication will fail."
      if (resolvedPassword.charAt(0).isWhitespace()) {
        authWarning = sprintf(message, ["leading"])
      }

      if (resolvedPassword.charAt(resolvedPassword.length() - 1).isWhitespace()) {
        authWarning = sprintf(message, ["trailing"])
      }
    }

    return new String(Base64.encoder.encode(("${username}:${resolvedPassword}").bytes))
  }

  String getBasicAuthHeader() {
    def basicAuth = this.getBasicAuth()
    return basicAuth ? "Basic $basicAuth" : null
  }

  /*
   * Parsed according to http://www.ietf.org/rfc/rfc2617.txt
   */
  public AuthenticateDetails parseBearerAuthenticateHeader(String header) {
    String realmKey = "realm"
    String serviceKey = "service"
    String scopeKey = "scope"
    AuthenticateDetails result = new AuthenticateDetails()

    // Each parameter has the form <token>=(<token>|<quoted-string>).
    while (header.length() > 0) {
      String key
      String value

      def keyEnd = header.indexOf("=")
      if (keyEnd == -1) {
        throw new DockerRegistryAuthenticationException("Www-Authenticate header terminated with junk: '$header'.")
      }

      key = header.substring(0, keyEnd)
      header = header.substring(keyEnd + 1)
      if (header.length() == 0) {
        throw new DockerRegistryAuthenticationException("Www-Authenticate header unmatched parameter key: '$key'.")
      }

      // Parse a quoted string.
      if (header[0] == '"') {
        header = header.substring(1)

        def valueEnd = header.indexOf('"')
        if (valueEnd == -1) {
          throw new DockerRegistryAuthenticationException('Www-Authenticate header has unterminated " (quotation mark).')
        }

        value = header.substring(0, valueEnd)
        header = header.substring(valueEnd + 1)

        if (header.length() != 0) {
          if (header[0] != ",") {
            throw new DockerRegistryAuthenticationException("Www-Authenticate header params must be separated by , (comma).")
          }
          header = header.substring(1)
        }
      } else { // Parse an unquoted token.
        def valueEnd = header.indexOf(",")

        // In the case of the last parameter, there will be no terminating ',' character.
        if (valueEnd == -1) {
          value = header
          header = ""
        } else {
          value = header.substring(0, valueEnd)
          header = header.substring(valueEnd + 1)
        }
      }

      if (key.equalsIgnoreCase(realmKey)) {
        def url = new URL(value)
        result.realm = url.protocol + "://" + url.authority
        result.path = url.path
        if (result.path.length() > 0 && result.path[0] == "/") {
          result.path = result.path.substring(1)
        }
      } else if (key.equalsIgnoreCase(serviceKey)) {
        result.service = value
      } else if (key.equalsIgnoreCase(scopeKey)) {
        result.scope = value
      }
    }

    if (!result.realm) {
      throw new DockerRegistryAuthenticationException("Www-Authenticate header must provide 'realm' parameter.")
    }
    if (!result.service) {
      // This e.g. is the case for OpenShift Container Registry
      result.service = null
    }

    return result
  }

  private getTokenService(String realm) {
    def tokenService = realmToService.get(realm)

    if (tokenService == null) {
      tokenService = serviceClientProvider.getService(TokenService, new DefaultServiceEndpoint("tokenservice", realm))
      realmToService[realm] = tokenService
    }

    return tokenService
  }

  public DockerBearerToken getToken(String repository) {
    return cachedTokens[repository]
  }

  public DockerBearerToken getToken(String repository, String authenticateHeader) {
    def authenticateDetails
    try {
      authenticateDetails = parseBearerAuthenticateHeader(authenticateHeader)
    } catch (Exception e) {
      throw new DockerRegistryAuthenticationException("Failed to parse www-authenticate header: ${e.message}")
    }

    def tokenService = getTokenService(authenticateDetails.realm)
    def token
    try {
      if (basicAuthHeader) {
        token = Retrofit2SyncCall.execute(tokenService.getToken(authenticateDetails.path, authenticateDetails.service, authenticateDetails.scope, basicAuthHeader, userAgent))
      } else {
        token = Retrofit2SyncCall.execute(tokenService.getToken(authenticateDetails.path, authenticateDetails.service, authenticateDetails.scope, userAgent))
      }
    } catch (Exception e) {
      if (authWarning) {
        throw new DockerRegistryAuthenticationException("Authentication failed ($authWarning): ${e.getMessage()}", e)
      } else {
        throw new DockerRegistryAuthenticationException("Authentication failed: ${e.getMessage()}", e)
      }
    }

    cachedTokens[repository] = token
    return token
  }

  public void clearToken(String repository) {
    cachedTokens.remove(repository)
  }

  private interface TokenService {
    @GET("/{path}")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<DockerBearerToken> getToken(@Path(value="path", encoded=true) String path,
                                     @Query(value="service") String service, @Query(value="scope") String scope,
                                     @Header("User-Agent") String agent)

    @GET("/{path}")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Call<DockerBearerToken> getToken(@Path(value="path", encoded=true) String path, @Query(value="service") String service,
                               @Query(value="scope") String scope, @Header("Authorization") String basic,
                               @Header("User-Agent") String agent)
  }

  private class AuthenticateDetails {
    String realm
    String path
    String service
    String scope
  }
}
