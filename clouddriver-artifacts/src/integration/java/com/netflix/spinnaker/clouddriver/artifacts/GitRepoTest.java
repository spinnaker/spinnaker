/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitRepoArtifactProviderProperties;
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitRepoFileSystem;
import com.netflix.spinnaker.clouddriver.artifacts.utils.GiteaContainer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location = classpath:clouddriver.yml"})
public class GitRepoTest {

  private static final GiteaContainer giteaContainer = new GiteaContainer();
  @LocalServerPort int port;

  static {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    giteaContainer.start();
  }

  public String baseUrl() {
    return "http://localhost:" + port;
  }

  @DisplayName(
      ".\n===\n"
          + "Given a gitrepo account with user/pass credentials\n"
          + "When sending download artifact request\n"
          + "Then the repo is downloaded\n===")
  @Test
  public void shouldDownloadGitRepoWithUserPass() throws IOException, InterruptedException {
    // given
    Map<String, Object> body =
        ImmutableMap.of(
            "artifactAccount", "basic-auth",
            "reference", giteaContainer.httpUrl(),
            "type", "git/repo",
            "version", "master");
    deleteTmpClone(body);

    // when
    Response response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());

    // then
    byte[] bytes = response.getBody().asByteArray();
    assertBytesHaveFile(bytes, "README.md");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a gitrepo account with access token\n"
          + "When sending download artifact request\n"
          + "Then the repo is downloaded\n===")
  @Test
  public void shouldDownloadGitRepoWithToken() throws IOException, InterruptedException {
    // given
    Map<String, Object> body =
        ImmutableMap.of(
            "artifactAccount", "token-auth",
            "reference", giteaContainer.httpUrl(),
            "type", "git/repo",
            "version", "master");
    deleteTmpClone(body);

    // when
    Response response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());

    // then
    byte[] bytes = response.getBody().asByteArray();
    assertBytesHaveFile(bytes, "README.md");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a git repo url without '.git' suffix\n"
          + "When sending download artifact request\n"
          + "Then the repo is downloaded\n===")
  @Test
  public void shouldDownloadGitRepoWithoutUrlSuffix() throws IOException, InterruptedException {
    // given
    Map<String, Object> body =
        ImmutableMap.of(
            "artifactAccount", "token-auth",
            "reference", giteaContainer.httpUrl().replaceAll(".git$", ""),
            "type", "git/repo",
            "version", "master");
    deleteTmpClone(body);

    // when
    Response response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());

    // then
    byte[] bytes = response.getBody().asByteArray();
    assertBytesHaveFile(bytes, "README.md");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a gitrepo account with ssh keys\n"
          + "When sending download artifact request\n"
          + "Then the repo is downloaded\n===")
  @Test
  public void shouldDownloadGitRepoWithSsh() throws IOException, InterruptedException {
    // given
    Map<String, Object> body =
        ImmutableMap.of(
            "artifactAccount", "ssh-auth",
            "reference", giteaContainer.sshUrl(),
            "type", "git/repo",
            "version", "master");
    deleteTmpClone(body);

    // when
    Response response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());

    // then
    byte[] bytes = response.getBody().asByteArray();
    assertBytesHaveFile(bytes, "README.md");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a gitrepo account with ssh keys\n"
          + "  And a known_hosts file\n"
          + "When sending download artifact request\n"
          + "Then the repo is downloaded\n===")
  @Test
  public void shouldDownloadGitRepoWithSshAndKnownHosts() throws IOException, InterruptedException {
    // given
    Map<String, Object> body =
        ImmutableMap.of(
            "artifactAccount", "ssh-auth-known-hosts",
            "reference", giteaContainer.sshUrl(),
            "type", "git/repo",
            "version", "master");
    deleteTmpClone(body);

    // when
    Response response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());

    // then
    byte[] bytes = response.getBody().asByteArray();
    assertBytesHaveFile(bytes, "README.md");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a previously downloaded gitrepo artifact\n"
          + "  And a new file added to the repo\n"
          + "When sending a second download artifact request\n"
          + "Then the new file is included\n===")
  @Test
  public void shouldDownloadGitRepoUpdates() throws IOException, InterruptedException {
    // given
    Map<String, Object> body =
        ImmutableMap.of(
            "artifactAccount", "token-auth",
            "reference", giteaContainer.httpUrl(),
            "type", "git/repo",
            "version", "master");
    deleteTmpClone(body);
    Response response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());
    giteaContainer.addFileToRepo("newfile");

    // when
    response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());

    // then
    byte[] bytes = response.getBody().asByteArray();
    assertBytesHaveFile(bytes, "README.md", "newfile");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a gitrepo account\n"
          + "When sending download artifact request including a subdirectory\n"
          + "Only the subdirectory is downloaded\n===")
  @Test
  public void shouldDownloadGitRepoSubdir() throws IOException, InterruptedException {
    // given
    Map<String, Object> body =
        ImmutableMap.of(
            "artifactAccount", "token-auth",
            "reference", giteaContainer.httpUrl(),
            "type", "git/repo",
            "version", "master",
            "location", "subdir");
    deleteTmpClone(body);
    giteaContainer.addFileToRepo("subdir/subfile");

    // when
    Response response =
        given().body(body).contentType("application/json").put(baseUrl() + "/artifacts/fetch");
    if (response.statusCode() != 200) {
      response.prettyPrint();
    }
    assertEquals(200, response.statusCode());

    // then
    byte[] bytes = response.getBody().asByteArray();
    assertBytesHaveFile(bytes, "subdir/subfile");
  }

  private void assertBytesHaveFile(byte[] bytes, String... files)
      throws IOException, InterruptedException {
    Path archive = Paths.get(System.getenv("BUILD_DIR"), "downloads", "repo.tgz");
    if (archive.toFile().getParentFile().exists()) {
      FileUtils.forceDelete(archive.toFile().getParentFile());
      FileUtils.forceMkdir(archive.toFile().getParentFile());
    }
    FileUtils.writeByteArrayToFile(archive.toFile(), bytes);
    Process process =
        new ProcessBuilder("tar", "-zxvf", "repo.tgz")
            .directory(archive.toFile().getParentFile())
            .redirectErrorStream(true)
            .start();
    process.waitFor();
    assertEquals(
        0,
        process.exitValue(),
        IOUtils.toString(process.getInputStream(), Charset.defaultCharset()));
    for (String file : files) {
      assertTrue(
          Paths.get(archive.toFile().getParent(), file).toFile().exists(),
          "File " + file + " not found in response");
    }
  }

  private static void deleteTmpClone(Map<String, Object> artifact) throws IOException {
    Path localClone =
        new GitRepoFileSystem(new GitRepoArtifactProviderProperties())
            .getLocalClonePath(
                (String) artifact.get("reference"), (String) artifact.get("version"));
    if (localClone.toFile().exists()) {
      FileUtils.forceDelete(localClone.toFile());
    }
    FileUtils.forceMkdir(localClone.toFile());
  }
}
