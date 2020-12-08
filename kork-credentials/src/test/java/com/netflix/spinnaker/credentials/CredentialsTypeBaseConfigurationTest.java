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
 *
 */

package com.netflix.spinnaker.credentials;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.credentials.definition.*;
import java.util.List;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticApplicationContext;

public class CredentialsTypeBaseConfigurationTest {
  private static final String CREDENTIALS_TYPE = "test";
  private StaticApplicationContext context;
  private CredentialsTypeBaseConfiguration<TestCredentials, TestAccount> config;
  private CredentialsTypeProperties<TestCredentials, TestAccount> props;

  @BeforeEach
  public void setup() {
    CredentialsDefinitionSource<TestAccount> source =
        () -> List.of(new TestAccount("account1"), new TestAccount("account2"));
    props =
        CredentialsTypeProperties.<TestCredentials, TestAccount>builder()
            .type(CREDENTIALS_TYPE)
            .credentialsClass(TestCredentials.class)
            .credentialsDefinitionClass(TestAccount.class)
            .defaultCredentialsSource(source)
            .credentialsParser(TestCredentials::new)
            .build();
    context = new StaticApplicationContext();
    config = new CredentialsTypeBaseConfiguration<>(context, props);
  }

  @Test
  public void testCreateCredentials() {
    config.afterPropertiesSet();
    // This test runner ignores PostConstruct annotations
    config.getCredentialsLoader().load();
    CredentialsRepository<TestCredentials> repository =
        context.getBean(CredentialsRepository.class);
    assertThat(repository).isNotNull();
    assertThat(repository.getOne("account1")).isNotNull();
  }

  @Test
  public void testOverrideParser() {
    context.registerBean("customParser", TestCustomParser.class);
    config.afterPropertiesSet();
    // This test runner ignores PostConstruct annotations
    config.getCredentialsLoader().load();
    context.getBean(AbstractCredentialsLoader.class);

    CredentialsRepository<TestCredentials> repository =
        context.getBean(CredentialsRepository.class);
    assertThat(repository).isNotNull();
    TestCredentials cred = repository.getOne("account1");
    assertThat(cred).isNotNull();
    assertThat(cred.getProperty()).isNotBlank().isEqualTo("my-value");
  }

  @Test
  public void testOverrideLifecycleHandler() {
    TestLifecycleHandler handler = Mockito.mock(TestLifecycleHandler.class);
    context.getBeanFactory().registerSingleton("customLifecycleHandler", handler);
    config.afterPropertiesSet();
    // This test runner ignores PostConstruct annotations
    config.getCredentialsLoader().load();

    CredentialsRepository<TestCredentials> repository =
        context.getBean(CredentialsRepository.class);
    assertThat(repository).isNotNull();
    TestCredentials cred = repository.getOne("account1");
    Mockito.verify(handler).credentialsAdded(cred);
  }

  @Test
  public void testOverrideSource() {
    CredentialsDefinitionSource<TestAccount> source =
        new TestSource(List.of(new TestAccount("account3"), new TestAccount("account4")));
    context.getBeanFactory().registerSingleton("customSource", source);
    config.afterPropertiesSet();
    // This test runner ignores PostConstruct annotations
    config.getCredentialsLoader().load();

    CredentialsRepository<TestCredentials> repository =
        context.getBean(CredentialsRepository.class);
    assertThat(repository).isNotNull();
    assertThat(repository.getOne("account3")).isNotNull();
    assertThat(repository.getOne("account1")).isNull();
  }

  @Test
  public void testOverrideCredentialsRepository() {
    TestCredentialsRepository repository =
        new TestCredentialsRepository(CREDENTIALS_TYPE, new NoopCredentialsLifecycleHandler<>());
    context.getBeanFactory().registerSingleton("customRepository", repository);
    config.afterPropertiesSet();
    // This test runner ignores PostConstruct annotations
    config.getCredentialsLoader().load();

    String[] beanNames = context.getBeanNamesForType(CredentialsRepository.class);
    assertThat(beanNames).hasSize(1);
    assertThat(beanNames[0]).isEqualTo("customRepository");
  }

  @Test
  public void testParallel() {
    CredentialsDefinitionSource<TestAccount> source = () -> List.of(new TestAccount("account1"));
    props =
        CredentialsTypeProperties.<TestCredentials, TestAccount>builder()
            .type(CREDENTIALS_TYPE)
            .credentialsClass(TestCredentials.class)
            .credentialsDefinitionClass(TestAccount.class)
            .defaultCredentialsSource(source)
            .credentialsParser(TestCredentials::new)
            .parallel(true)
            .build();
    context = new StaticApplicationContext();
    config = new CredentialsTypeBaseConfiguration<>(context, props);
    TestCredentialsRepository repository =
        new TestCredentialsRepository(CREDENTIALS_TYPE, new NoopCredentialsLifecycleHandler<>());
    context.getBeanFactory().registerSingleton("customRepository", repository);
    config.afterPropertiesSet();
    // This test runner ignores PostConstruct annotations
    config.getCredentialsLoader().load();

    BasicCredentialsLoader<?, TestCredentials> loader =
        context.getBean(BasicCredentialsLoader.class);
    assertThat(loader).isNotNull();
    assertThat(loader.isParallel()).isTrue();
  }

  private static class TestCredentialsRepository
      extends MapBackedCredentialsRepository<TestCredentials> {

    public TestCredentialsRepository(
        String type, CredentialsLifecycleHandler<TestCredentials> eventHandler) {
      super(type, eventHandler);
    }
  }

  @Data
  private static class TestAccount implements CredentialsDefinition {
    private final String name;
  }

  @Data
  private static class TestSource implements CredentialsDefinitionSource<TestAccount> {
    private final List<TestAccount> credentialsDefinitions;
  }

  private static class TestCustomParser implements CredentialsParser<TestAccount, TestCredentials> {
    @Override
    public TestCredentials parse(TestAccount account) {
      TestCredentials creds = new TestCredentials(account);
      creds.setProperty("my-value");
      return creds;
    }
  }

  private static class TestLifecycleHandler
      extends NoopCredentialsLifecycleHandler<TestCredentials> {}

  @Data
  private static class TestCredentials implements Credentials {
    private final TestAccount testAccount;
    private String property;

    public String getName() {
      return testAccount.getName();
    }

    @Override
    public String getType() {
      return CREDENTIALS_TYPE;
    }
  }
}
