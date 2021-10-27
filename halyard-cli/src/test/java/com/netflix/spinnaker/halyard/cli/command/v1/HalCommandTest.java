/*
 * Copyright 2021 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.cli.command.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
class HalCommandTest {

  private HalCommand hal;

  private JCommander jc;

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  @BeforeEach
  void setup(TestInfo testInfo) {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    hal = new HalCommand();
    jc = new JCommander(hal);
    jc.setVerbose(1);
    hal.setCommander(jc).configureSubcommands();
  }

  @AfterEach
  public void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @ParameterizedTest(name = "noArgsWhenOneIsRequired {0}")
  @MethodSource("commandProvider")
  void noArgsWhenOneIsRequired(String[] args, String expectedMessage) throws Exception {
    // jcommander's validation isn't enough to fail in this case.  It is if we
    // specify arity = 1, but then correctly passing one argument also fails, so
    // we rely on validation in halyard to catch this, which doesn't happen
    // until executing the command.
    jc.parse(args);

    catchSystemExit(() -> hal.execute());

    assertThat(errContent.toString()).contains(expectedMessage);
  }

  @ParameterizedTest(name = "oneArgWhenOneIsRequired {0}")
  @MethodSource("commandProvider")
  void oneArgWhenOneIsRequired(String[] args) throws Exception {
    jc.parse(ArrayUtils.add(args, "foo"));

    // Here, running hal.execute() actually attempts to connect to the halyard
    // daemon since the command is valid.
    catchSystemExit(() -> hal.execute());

    assertThat(errContent.toString()).contains("Is your daemon running");
  }

  @ParameterizedTest(name = "twoArgsWhenOneIsRequired {0}")
  @MethodSource("commandProvider")
  void twoArgsWhenOneIsRequired(String[] args) {
    // And here, jcommander's built-in validation catches the error.  Perhaps
    // one day specifying "arity = 1" in the relevant @Parameter annotations
    // will work, and then the message to expect is "There should be exactly 1
    // main parameters but 2 were found".
    assertThatThrownBy(() -> jc.parse(ArrayUtils.addAll(args, "foo", "bar")))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Only one main parameter allowed but found several");
  }

  void testHalyardVersionBomNoArgs() throws Exception {
    // hal version bom takes an optional VERSION.  So it's supposed to work with
    // no args.
    String[] args = {"hal", "version", "bom", "--deployment", "deployment"};
    jc.parse(args);
    catchSystemExit(() -> hal.execute());

    assertThat(errContent.toString()).contains("Failed to get version of Spinnaker");
  }

  void testHalyardVersionBomOneArg() throws Exception {
    // hal version bom takes an optional VERSION.  So it's supposed to work with
    // 1 arg.
    String[] args = {"hal", "version", "bom", "my-bom-version"};
    jc.parse(args);
    catchSystemExit(() -> hal.execute());

    assertThat(errContent.toString()).contains("Is your daemon foo");
  }

  void testHalyardVersionBomMoreThanOneArg() {
    // hal version bom takes an optional VERSION, but more than one is invalid
    String[] args = {"hal", "version", "bom", "my-bom-version", "another-bom-version"};
    assertThatThrownBy(() -> jc.parse(ArrayUtils.addAll(args)))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Only one main parameter allowed but found several");
  }

  // Inspired by https://stackoverflow.com/a/46931618
  static Stream<Arguments> commandProvider() {
    // These are examples of commands that require on argument...not an
    // exhaustive list.  It attempts to cover all the base classes that declare
    // things like accounts that require one name, but not all the child
    // classes.  For example, for providers/account/AbstractHasAccountCommand,
    // any account command for any provider is sufficient.
    return Stream.of(
        Arguments.of((Object) new String[] {"admin", "publish", "latest"}, "No version supplied"),
        Arguments.of(
            (Object) new String[] {"admin", "publish", "latest-halyard"}, "No version supplied"),
        Arguments.of(
            (Object) new String[] {"admin", "publish", "latest-spinnaker"}, "No version supplied"),
        Arguments.of(
            (Object)
                new String[] {
                  "admin", "publish", "profile", "--bom-path", "path", "--profile-path", "path"
                },
            "No artifact name supplied"),
        Arguments.of(
            (Object) new String[] {"config", "artifact", "gcs", "account", "add"},
            "No account name supplied"),
        Arguments.of(
            (Object)
                new String[] {
                  "config", "artifact", "templates", "delete", "--deployment", "my-deploy"
                },
            "No template supplied"),
        Arguments.of(
            (Object)
                new String[] {"config", "ci", "codebuild", "account", "add", "--region", "region"},
            "No account name supplied"),
        Arguments.of(
            (Object)
                new String[] {"config", "ci", "jenkins", "master", "add", "--address", "address"},
            "No master name supplied"),
        Arguments.of(
            (Object) new String[] {"config", "notification", "pubsub", "google", "add"},
            "No topic name supplied"),
        Arguments.of(
            (Object)
                new String[] {
                  "config",
                  "provider",
                  "azure",
                  "bakery",
                  "base-image",
                  "add",
                  "--publisher",
                  "publisher",
                  "--sku",
                  "sku",
                  "--offer",
                  "offer"
                },
            "No base image name supplied"),
        Arguments.of(
            (Object)
                new String[] {"config", "provider", "dcos", "cluster", "add", "--dcos-url", "url"},
            "No cluster name supplied"),
        Arguments.of(
            (Object) new String[] {"config", "provider", "kubernetes", "account", "add"},
            "No account name supplied"),
        Arguments.of(
            (Object) new String[] {"config", "pubsub", "google", "subscription", "add"},
            "No subscription name supplied"),
        Arguments.of(
            (Object)
                new String[] {
                  "config",
                  "repository",
                  "artifactory",
                  "search",
                  "add",
                  "--username",
                  "user",
                  "--password",
                  "password",
                  "--repo",
                  "repo",
                  "--base-url",
                  "url",
                  "--groupId",
                  "groupId"
                },
            "No search name supplied"),
        Arguments.of(
            (Object) new String[] {"plugins", "repository", "add", "--url", "url"},
            "No plugin repository supplied"),
        Arguments.of(
            (Object)
                new String[] {
                  "task", "interrupt",
                },
            "No UUID supplied"));

    // Testing canary commands doesn't work in this setup, as they try to
    // contact the daemon to get canary information before the "real" command.
    // Perhaps mocking the (static) Daemon methods makes for a cleaner, more
    // complete test?  Until then, commenting this out.
    // Arguments.of((Object)new String[]{"config", "canary", "aws", "account", "add",
    // "--deployment", "my-deploy"}, "No canary account name supplied")

    // For some reason, this command causes hal.execute to output "null"...
    // Arguments.of((Object)new String[]{"plugins", "add", "--deployment", "deployment"}, "No plugin
    // supplied")
  }
}
