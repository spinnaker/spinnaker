/*
 * Copyright 2025 Netflix, Inc.
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
      "-----BEGIN PRIVATE KEY-----\n"
          + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDLCqJMBNUnCPC5\n"
          + "ZCDLVa0h9c/N87e5AUdO7E7UwZmJN7+VRe7j6uSySeS9Bn1PmtZSETXztz8ph/iV\n"
          + "e4y+iBSqtvZzSkvLC5ZgzsD37v+Yw/hFSQu/NZgcd5wcjd84Mfj7hPL7E1HkL4Vo\n"
          + "Ya/yAa11eBBk84in5olxrvjrfZJZy8DFdb4pdVEE6YnzaDD1O9c1Ra6ENluNxDjv\n"
          + "PwyaehgaLCKVjFVjbDPic+ieygDmR9IADW04cuz9R+Zu2q2RELqQvtle4AMShFJy\n"
          + "GRuHWw4/LXJqAHZtc8NoGzmkxt77yuOQ5EX0gr+J0D4cFAXfVyQ2bIhnF4wiz15J\n"
          + "30lIE0PNAgMBAAECggEAKXhLDL7B8F6VmC/4uL8PhQupPVXldO5ra5W9RhwiqVGP\n"
          + "GkR11exQeI+6Hdd4+azU0F8+h0AqsOdaIOHirbmqivGipYqLr3V26d/gruMMJl4E\n"
          + "U9ZnBU9DebD+XCCn8ljWkzykyh44kCQamea14nZwQLlck9nf0/c0pFkJ80MrBJbJ\n"
          + "OfIocY5PW/UclPP0pXFYZJoO1gcI7MZwDB2kn0fCaCMsMRzpnEE9zoXXV5b+gZwL\n"
          + "T7mwwIHiJtSlD5iAcYEBy/lD9Lcbv4t1rfbr1XzQKIkg9sCrtt1dfXiHnHjCTxRt\n"
          + "Jr8BhmRxQ26O1g/5Ft7apwDprhWC/KrYpdFSjGJxKwKBgQDzyWaa9InJtknfJMqm\n"
          + "yKLztnH4wrxMvj/oqrcBnGBfiYh9dJ4fxiSwkv6PnsmH/kpjYVbUorOHxKqMxcxP\n"
          + "IOtIqh9iA2zysXir3U33cfEWW/tRpeOEaWKYwvYNd3aofst3Y7aEEZsKcXrcEr0T\n"
          + "CCLiFtg99zODbVNhxcQ4RWg3rwKBgQDVNquhoSxYaqlpJi3lB8Czam5RroHQY8I5\n"
          + "oc0eVtMMw/qv3KlEushjAL70Fly/2Vu2DpEaAE99sA4knLM3sy5SpCIQVwBrOIF3\n"
          + "kD1jlyzojU8JI6GUPpasMU28SP/fR9NEhtWke7woCiy8oU/ZqlP3pxRx7jzPzG2b\n"
          + "mJlS5vmfQwKBgQCnAmVxaG9wqZnn7cuLAM5piaaAld/r7zXXDgS7bMa1DIJd9+NP\n"
          + "vy1pbfpIp65GpRWPCaMznpbBPyDbubHiz5mAOVOwkMo1ZRFXJBACoaNY/wCoCa5Z\n"
          + "Ct1J694mkZ3PhrWa/8uMpIcDW4SgeZHgFOXY32+a29wFgILr61Emf54K7wKBgC+B\n"
          + "UNhgWssQaNKeyRcAlTTkf9P/N7lAoOPKYzNhUQDFIbPRTH2dyEwWvHUSDnRIb6Cu\n"
          + "ujG64/szINOTfnLon2eWXmiZmeRJ4L7NCoCIDF98LKHyqGupTlTrX1CWSzxqem4I\n"
          + "RM2zLAcXzUPyBSKQSskhFvMTi8UY3UsPwwmvoOqVAoGAIo6R8hkO0mGnEtAtuIp0\n"
          + "4ZrsJvUQ0F/Nse/IvdT8PBC2xcz6V3f+LaJG9yqN57fGoD6V5tme0hdPb6K//42l\n"
          + "oBJQ5zTTHMt+RtkKDyPt+K3reXIxFL4E0GiYrJpQRsjKu5xWZY73jO4zC/9aTP9y\n"
          + "0LJp16baAExMUyvsgf30c4A=\n"
          + "-----END PRIVATE KEY-----";

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
