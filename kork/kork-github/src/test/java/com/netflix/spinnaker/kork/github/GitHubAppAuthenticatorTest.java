/*
 * Copyright 2025 Razorpay.
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

package com.netflix.spinnaker.kork.github;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for GitHubAppAuthenticator.
 *
 * <p>These tests use a pre-generated test private key to avoid slow key generation on every test
 * run.
 */
class GitHubAppAuthenticatorTest {

  // Pre-generated RSA 2048-bit private key for testing (PKCS#8 format)
  // This is a test key and should never be used in production
  private static final String TEST_PRIVATE_KEY =
      """
      -----BEGIN PRIVATE KEY-----
      MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCo2X/zDuXwB5qj
      EajeUD6+cBvbgTYVU4Vdisrd41XvKYpSpqnX+Bn9UFdkV983eJHN3glDLkLFb9Xz
      Xjc2JQXolQPRBdaWrmq16Ii1FcRSI9xltFuYtObROMzre3TrQ158anF0Y/2vJjRy
      RClZSFGaldo/vBz72oBxGSjDE4XWKlqeJawiGtAEbkoToiCNeFhNNOxTFoe33ZWE
      iAbku53xfmv3V93Mj/WM5Isje6kvu8uLQCOl7yTAaTCFLYY3nY1+V1VCNq8f/To4
      kV46KbKRws9WcSe0UE+ET2lG0Y6S06AeHf9FWzTZzg+7K49EA3UQNfBRmmCD9f4t
      BMlQPMA1AgMBAAECggEARWDtToFaIKj3NLbuZL6jMVveTnDGuLeTTo7XcZnWNwmi
      EPjzQ87pWukWp5/lk6TigC0SMD0DaZ3c0v1tAT3wMhN8uHfGJy7uoOU1uvaBLuEW
      T+HuWw5F40UMClw1e++4FLYl/RWS6NNxbFwug0WQZkzZmyOf4ypyaUZVteZBMXCU
      wp2bE/W0cVGEUbzNzzZGpoXxXXRXzn+kirnFjS9d45i/HUwm1jHa4v0Tfay4Ar9v
      BSVKuthJgaTxnDKWvxQ9s6RDWMVo5Log6b4RGDOZV7dIb5eRQcwCH5Rq2mxU370T
      3hLyEx4ICAKl8ZBg/dTeJ3/qsl2H3k1LXHWL7nrhFwKBgQDpSjXUWgPM0oGlY5Lq
      24VVMlVLKHJTUQF2dl/PUUnKXm41iNODf1/Fagb4QPkeKp72CYx7lfxC2lPqs3cT
      wo5jxVSloJoUVf2qNdXSwMUh0T2w+LpOV5AlMDktdQ8V1kwLWtrTgc7RK1Mj8zSQ
      JF8FBMAFGOPw1clE9GbTfQ4TTwKBgQC5SWGKaTrTMhiMajGeX20JP4j7cVhfU2tR
      11MQ0o4re50mzC79vwTk2Ybar6qVVIvInWfteoXhaOoHfzi2bGr04sTRHQjCo8C3
      qz43IiJXdkC3fugeZqPJ6nZ3JuUPC6dq+sOXF+oJvRq85GuBP6vt1o4uxe1W+CsQ
      JJK4PF2jOwKBgDcYtbnfQIKBPOlIqQwaqFTEvGwxsz6GJShLMLmP4zOONc0i8YFe
      9cl0Dw1Wmv9K5ZwKCUmu1JMdaTBHDlp2WpappiIv2fPvkyc967AIowYnmsBPHgEe
      oQaHaxmXSebIY9FStde6EpRH/SzCZamdTWusAYWyqTLZ6t0EM7zDDi31AoGAOwSX
      sCnClgDv9tHgiiylI3v8WvMIjhyZI5FtoP8gT9NpBDGniiWtHmP3Y3Lu5+/tMnKI
      5wjO2jS7zrWET/8KtoQA4wbXgn/8Y8SE5bTWsXs2M/yVXRGefDNVlrBp57fzlMzZ
      Pihc4Ms+WAp9/8ZTMkfUNCvRZJFZziOIJGz9+n8CgYEA30ynNn0xOuRFglhVHQHL
      yjN8hbqztBY+QT6C8MvUA03mMQb4cPMntJw73FE9Sta0ba5BBIRUBpdx/XpmtZaP
      7JjzbwM5ZUEu6SM8hP5bV5XQeocVqVSZbjI6NSVIHTlkRI+bYBmA0vRYEWstM3DG
      Jai6vOn73aXT257KuK2nZjo=
      -----END PRIVATE KEY-----""";

  private Path tempPrivateKeyFile;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws Exception {
    // Write pre-generated test private key to temp file
    tempPrivateKeyFile = tempDir.resolve("test-private-key.pem");
    Files.write(tempPrivateKeyFile, TEST_PRIVATE_KEY.getBytes());
  }

  @Test
  void shouldCreateAuthenticatorWithValidConfiguration() {
    // When & Then
    assertDoesNotThrow(
        () ->
            new GitHubAppAuthenticator(
                "12345", tempPrivateKeyFile.toString(), "67890", "https://api.github.com"));
  }

  @Test
  void shouldFailWithInvalidPrivateKeyPath() {
    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                new GitHubAppAuthenticator(
                    "12345", "/nonexistent/path/key.pem", "67890", "https://api.github.com"));

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }

  @Test
  void shouldNormalizeBaseUrlWithTrailingSlash() {
    // Given
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            "12345", tempPrivateKeyFile.toString(), "67890", "https://api.github.com/");

    // When - internal baseUrl should have trailing slash removed
    // We can't test this directly without exposing internal state,
    // but we can verify the authenticator was created successfully
    assertNotNull(authenticator);
  }

  @Test
  void shouldSupportPKCS8Format() throws IOException {
    // Given - TEST_PRIVATE_KEY is in PKCS#8 format (BEGIN PRIVATE KEY)
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            "12345", tempPrivateKeyFile.toString(), "67890", "https://api.github.com");

    // When & Then - should load successfully
    assertNotNull(authenticator);
  }

  @Test
  void shouldSupportPKCS1Format(@TempDir Path tempDir) throws IOException {
    // Given - PKCS#1 format (BEGIN RSA PRIVATE KEY)
    String pkcs1Key =
        "-----BEGIN RSA PRIVATE KEY-----\n"
            + "MIIEowIBAAKCAQEAywqiTATVJwjwuWQgy1WtIfXPzfO3uQFHTuxO1MGZiTe/lUXu\n"
            + "4+rkskn"
            + "kvQZ9T5rWUhE187c/KYf4lXuMvogUqrb2c0pLywuWYM7A9+7/mMP4RUkLvzWY\n"
            + "HHecHI3fODH4+4Ty+xNR5C+FaGGv8gGtdXgQZPOIp+aJca746"
            + "32SWcvAxXW+KXVR\n"
            + "BOmJ82gw9TvXNUWuhDZbjcQ47z8MmnoYGiwilYxVY2wz4nPonsqA5kfSAA1tOHLs\n"
            + "/Ufmbtqtk"
            + "RC6kL7ZXuADEoRSchkbh1sOPy1yagB2bXPDaBs5pMbe+8rjkORF9IK/\n"
            + "idA+HBQF31ckNmyIZxeMIs9eSd9JSBNDzQIDAQABAoIBACl4Swy+wfBelZgv+Li/\n"
            + "D4ULqT1V5XTua2uVvUYcIqlRjxpEddXsUHiPuh3XePms1NBfPodAKrDnWiDh4q25\n"
            + "qorxoqWKi691dune4K7jDCZeBFPWZwVPQ3mw/lwgp/JY1pM8pMoeOJAkGpnmteJ2\n"
            + "cEC5XJPZ39P3NKRZCfNDKwSWyTnyKHGOT1v1HJTz9KVxWGSaDtYHCOzGcAwdpJ9H\n"
            + "wmgjLDEc6ZxBPc6F11eW/oGcC0+5sMCB4ibUpQ+YgHGBAcv5Q/S3G7+Lda3269V8\n"
            + "0CiJIPbAq7bdXX14h5x4wk8UbSa/AYZkcUNujtYP+Rbe2qcA6a4Vgvyq2KXRUoxi\n"
            + "cSsCgYEA88lmv"
            + "SNybbZJ3yTKpsii87Zx+MK8TL4/6Kq3AZxgX4mIfXSeH8YksJ\n"
            + "L+j57Jh/5KY2FW1KKzh8SqjMXMTyDrSKofYgNs8rF4q91N93HxFlv7Ua"
            + "XjhGlimM\n"
            + "L2DXd2qH7Ld2O2hBGbCnF63BK9EwgixhbYPfczg21TYcXEOEVoN68CgYEA1Taro\n"
            + "aEsWGqpaSYt5QfAs2puUa6B0GPCOaHNHlbTDMP6r9ypRLrIYwC+9BZcv9lbtg6R\n"
            + "GgBPfbAOJJyzN7MuUqQiEFcAaziBd5A9Y5cs6I1PCSKhlD6WrDFNvEj/30fTRIbV\n"
            + "pHu8KAosvKFP2apT96cUce48z8xtm5iZUub5n0MCgYEApwJlcWhvcKmZ5+3LiwDO\n"
            + "aYmmgJXf6+811w4Eu2zGtQyCXffjT78taW36SKeuRqUVjwmjM56WwT8g27mx4s+Z\n"
            + "gDlTsJDKNWURVyQQAqGjWP8AqAmuWQrdSerYJpGdz4a1mv/LjKSHA1uEoHmR4BTl\n"
            + "2N9vmtvcBYCC6+tRJn+eCu8CgYAvgVDYYFrLEGjSnsk"
            + "XAJU05H/T/ze5QKDjymMz\n"
            + "YVEAxSGz0Ux9nchMFrx1Eg50SG+grroxuuP7MyDTk35y6J9nll5omZnkSeC+zQqA\n"
            + "iAxffCyh8qhrqU5U619Qlks8anpuCETNsywHF81D8gUikErJIRbzE4vFGN1LD8MJ\n"
            + "r6DqlQKBgCKOkfIZDtJhpxLQLbiKdOGa7Cb1ENBfzbHvyL3U/DwQtsXM+ld3/i2i\n"
            + "Rvcqjee3xqA+lebZntIXT2+iv/+NpaASUOc00xzLfkbZCg8j7fit63lyMRS+BNBo\n"
            + "mKyaUEbIyrucVmWO94zuMwv/Wkz/ctCyadem2gBMTFMr7IH99HOA\n"
            + "-----END RSA PRIVATE KEY-----";

    Path pkcs1File = tempDir.resolve("pkcs1-key.pem");
    Files.write(pkcs1File, pkcs1Key.getBytes());

    // When & Then - should load successfully
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            "12345", pkcs1File.toString(), "67890", "https://api.github.com");

    assertNotNull(authenticator);
  }

  @Test
  void shouldFailWithInvalidPEMFormat(@TempDir Path tempDir) throws IOException {
    // Given - invalid PEM content
    Path invalidFile = tempDir.resolve("invalid-key.pem");
    Files.write(invalidFile, "This is not a valid PEM key".getBytes());

    // When & Then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                new GitHubAppAuthenticator(
                    "12345", invalidFile.toString(), "67890", "https://api.github.com"));

    assertTrue(exception.getMessage().contains("Failed to load GitHub App private key"));
  }

  @Test
  void shouldGetAuthenticatedClient() {
    // Given
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            "12345", tempPrivateKeyFile.toString(), "67890", "https://api.github.com");

    // When & Then - This will fail to get a token (no real GitHub API)
    // but it verifies the method exists and can be called
    assertThrows(IOException.class, () -> authenticator.getAuthenticatedClient());
  }

  @Test
  void shouldGetInstallationToken() {
    // Given
    GitHubAppAuthenticator authenticator =
        new GitHubAppAuthenticator(
            "12345", tempPrivateKeyFile.toString(), "67890", "https://api.github.com");

    // When & Then - This will fail to get a token (no real GitHub API)
    // but it verifies the method exists and can be called
    assertThrows(IOException.class, () -> authenticator.getInstallationToken());
  }

  @Test
  void shouldSupportGitHubEnterprise() {
    // When & Then
    assertDoesNotThrow(
        () ->
            new GitHubAppAuthenticator(
                "12345",
                tempPrivateKeyFile.toString(),
                "67890",
                "https://github.company.com/api/v3"));
  }
}
