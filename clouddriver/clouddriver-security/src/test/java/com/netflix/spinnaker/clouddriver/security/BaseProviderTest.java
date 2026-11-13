/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BaseProviderTest {

  private final TestProvider provider = new TestProvider();

  @Test
  void reportsTheAccountsOfAccountAwareAgents() {
    provider.addAgents(
        List.of(
            new AccountAgent("type-1", "account-a"),
            new AccountAgent("type-2", "account-a"),
            new AccountAgent("type-3", "account-b"),
            new PlainAgent("type-4")));

    assertThat(provider.getScheduledAccounts()).containsExactlyInAnyOrder("account-a", "account-b");
    assertThat(ProviderUtils.getScheduledAccounts(provider))
        .containsExactlyInAnyOrder("account-a", "account-b");
  }

  @Test
  void removingAnAccountRemovesOnlyThatAccount() {
    AccountAgent a1 = new AccountAgent("type-1", "account-a");
    AccountAgent a2 = new AccountAgent("type-2", "account-a");
    AccountAgent b1 = new AccountAgent("type-3", "account-b");
    PlainAgent plain = new PlainAgent("type-4");
    provider.addAgents(List.of(a1, a2, b1, plain));

    provider.removeAgentsForAccounts(List.of("account-a", "account-without-agents"));

    assertThat(provider.getScheduledAccounts()).containsExactly("account-b");
    assertThat(provider.getAgents()).containsExactlyInAnyOrder(b1, plain);
  }

  @Test
  void anAccountIsScheduledAgainWhenItsAgentsAreAddedBack() {
    provider.addAgents(List.of(new AccountAgent("type-1", "account-a")));
    provider.removeAgentsForAccounts(List.of("account-a"));
    assertThat(provider.getScheduledAccounts()).isEmpty();

    provider.addAgents(List.of(new AccountAgent("type-1", "account-a")));

    assertThat(provider.getScheduledAccounts()).containsExactly("account-a");
  }

  @Test
  void returnsASnapshotThatLaterChangesDoNotAffect() {
    provider.addAgents(List.of(new AccountAgent("type-1", "account-a")));

    Set<String> before = ProviderUtils.getScheduledAccounts(provider);
    provider.addAgents(List.of(new AccountAgent("type-2", "account-b")));
    provider.removeAgentsForAccounts(List.of("account-a"));

    assertThat(before).containsExactly("account-a");
    assertThat(provider.getScheduledAccounts()).containsExactly("account-b");
  }

  @Test
  void listingScheduledAccountsDoesNotReadEveryAgent() {
    AtomicInteger accountNameReads = new AtomicInteger();
    for (int i = 0; i < 100; i++) {
      provider.addAgents(
          List.of(new CountingAccountAgent("type-" + i, "account-" + (i % 10), accountNameReads)));
    }
    accountNameReads.set(0);

    for (int i = 0; i < 5; i++) {
      assertThat(ProviderUtils.getScheduledAccounts(provider)).hasSize(10);
    }

    assertThat(accountNameReads).hasValue(0);
  }

  @Test
  void agentsWithoutAnAccountNameAreKeptButNotReported() {
    AccountAgent unnamed = new AccountAgent("type-1", null);
    AccountAgent named = new AccountAgent("type-2", "account-a");
    provider.addAgents(List.of(unnamed, named));

    assertThat(provider.getScheduledAccounts()).containsExactly("account-a");

    provider.removeAgentsForAccounts(List.of("account-a"));

    assertThat(provider.getScheduledAccounts()).isEmpty();
    assertThat(provider.getAgents()).containsExactly(unnamed);
  }

  @Test
  void anAgentThatCannotReportItsAccountIsStillAdded() {
    AccountAgent broken =
        new AccountAgent("type-1", "unused") {
          @Override
          public String getAccountName() {
            throw new IllegalStateException("no account");
          }
        };
    AccountAgent named = new AccountAgent("type-2", "account-a");

    provider.addAgents(List.of(broken, named));

    assertThat(provider.getAgents()).containsExactlyInAnyOrder(broken, named);
    assertThat(provider.getScheduledAccounts()).containsExactly("account-a");
  }

  @Test
  void anAgentEqualToOneAlreadyAddedIsNotIndexed() {
    provider.addAgents(List.of(new SameTypeAgent("type-1", "account-a")));

    provider.addAgents(List.of(new SameTypeAgent("type-1", "account-b")));

    assertThat(provider.getAgents()).hasSize(1);
    assertThat(provider.getScheduledAccounts()).containsExactly("account-a");
  }

  @Test
  void removalUsesTheAccountNameReportedWhenTheAgentWasAdded() {
    RenamableAgent agent = new RenamableAgent("type-1", "account-a");
    provider.addAgents(List.of(agent));

    agent.accountName = "account-b";
    provider.removeAgentsForAccounts(List.of("account-b"));

    assertThat(provider.getAgents()).isEmpty();
    assertThat(provider.getScheduledAccounts()).isEmpty();
  }

  @Test
  void concurrentAddsAndRemovalsKeepTheIndexInLineWithTheAgents() throws Exception {
    int accounts = 4;
    int threadsPerAccount = 2;
    int iterations = 2_000;
    List<AccountAgent> agents = new ArrayList<>();
    for (int i = 0; i < accounts; i++) {
      agents.add(new AccountAgent("type-" + i, "account-" + i));
    }

    ExecutorService executor = Executors.newFixedThreadPool(accounts * threadsPerAccount * 2);
    CyclicBarrier start = new CyclicBarrier(accounts * threadsPerAccount * 2);
    List<Future<?>> tasks = new ArrayList<>();
    try {
      for (AccountAgent agent : agents) {
        for (int t = 0; t < threadsPerAccount; t++) {
          tasks.add(
              executor.submit(
                  () -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                      provider.addAgents(List.of(agent));
                    }
                    return null;
                  }));
          tasks.add(
              executor.submit(
                  () -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                      provider.removeAgentsForAccounts(List.of(agent.getAccountName()));
                    }
                    return null;
                  }));
        }
      }
      for (Future<?> task : tasks) {
        task.get(1, TimeUnit.MINUTES);
      }
    } finally {
      executor.shutdownNow();
    }

    Set<String> accountsOfRemainingAgents =
        provider.getAgents().stream()
            .map(agent -> ((AccountAware) agent).getAccountName())
            .collect(Collectors.toSet());
    assertThat(provider.getScheduledAccounts()).isEqualTo(accountsOfRemainingAgents);
  }

  private static class TestProvider extends BaseProvider {
    @Override
    public String getProviderName() {
      return "test";
    }
  }

  private static class PlainAgent implements Agent {
    private final String agentType;

    PlainAgent(String agentType) {
      this.agentType = agentType;
    }

    @Override
    public String getAgentType() {
      return agentType;
    }

    @Override
    public String getProviderName() {
      return "test";
    }

    @Override
    public AgentExecution getAgentExecution(ProviderRegistry providerRegistry) {
      return agent -> {};
    }
  }

  private static class AccountAgent extends PlainAgent implements AccountAware {
    private final String accountName;

    AccountAgent(String agentType, String accountName) {
      super(agentType);
      this.accountName = accountName;
    }

    @Override
    public String getAccountName() {
      return accountName;
    }
  }

  /** Equal to any other instance with the same agent type, whatever its account. */
  private static class SameTypeAgent extends AccountAgent {
    SameTypeAgent(String agentType, String accountName) {
      super(agentType, accountName);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof SameTypeAgent
          && ((SameTypeAgent) o).getAgentType().equals(getAgentType());
    }

    @Override
    public int hashCode() {
      return getAgentType().hashCode();
    }
  }

  private static class RenamableAgent extends PlainAgent implements AccountAware {
    private volatile String accountName;

    RenamableAgent(String agentType, String accountName) {
      super(agentType);
      this.accountName = accountName;
    }

    @Override
    public String getAccountName() {
      return accountName;
    }
  }

  private static class CountingAccountAgent extends AccountAgent {
    private final AtomicInteger accountNameReads;

    CountingAccountAgent(String agentType, String accountName, AtomicInteger accountNameReads) {
      super(agentType, accountName);
      this.accountNameReads = accountNameReads;
    }

    @Override
    public String getAccountName() {
      accountNameReads.incrementAndGet();
      return super.getAccountName();
    }
  }
}
