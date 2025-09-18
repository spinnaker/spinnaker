/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver.sql.security

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.netflix.spinnaker.clouddriver.jackson.mixins.CredentialsDefinitionMixin
import com.netflix.spinnaker.clouddriver.security.AccessControlledAccountDefinition
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionMapper
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.kork.secrets.SecretSession
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.nhaarman.mockito_kotlin.mock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import java.time.Clock

class SqlAccountDefinitionRepositoryTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("overwrite fields when saving a new account with the same name as an existing account") {
      // given
      accountDefinitionRepository.save(testAccountOne)
      var accountOneDefinitions = accountDefinitionRepository.listByType("testTypeOne")
      assertThat(accountOneDefinitions.size).isEqualTo(1)
      assertThat(accountOneDefinitions[0]).isInstanceOf(TestAccountOne::class.java)

      // when
      accountDefinitionRepository.save(testAccountTwo)

      // then
      accountOneDefinitions = accountDefinitionRepository.listByType("testTypeOne")
      assertThat(accountOneDefinitions.size).isEqualTo(0)

      val accountTwoDefinitions = accountDefinitionRepository.listByType("testTypeTwo")
      assertThat(accountTwoDefinitions.size).isEqualTo(1)
      assertThat(accountTwoDefinitions[0]).isInstanceOf(TestAccountTwo::class.java)
    }

    test("overwrite fields when upserting a new account with the same name as an existing account") {
      // given
      accountDefinitionRepository.update(testAccountOne)
      var accountOneDefinitions = accountDefinitionRepository.listByType("testTypeOne")
      assertThat(accountOneDefinitions.size).isEqualTo(1)
      assertThat(accountOneDefinitions[0]).isInstanceOf(TestAccountOne::class.java)

      // when
      accountDefinitionRepository.update(testAccountTwo)

      // when
      accountOneDefinitions = accountDefinitionRepository.listByType("testTypeOne")
      assertThat(accountOneDefinitions.size).isEqualTo(0)

      val accountTwoDefinitions = accountDefinitionRepository.listByType("testTypeTwo")
      assertThat(accountTwoDefinitions.size).isEqualTo(1)
      assertThat(accountTwoDefinitions[0]).isInstanceOf(TestAccountTwo::class.java)
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!
    val jacksonBuilder: JsonMapper.Builder = jacksonMapperBuilder().addMixIn(CredentialsDefinition::class.java, CredentialsDefinitionMixin::class.java)
      .registerSubtypes(TestAccountOne::class.java, TestAccountTwo::class.java)
    val mapper = AccountDefinitionMapper(
      jacksonBuilder.build(),
      mock<AccountDefinitionSecretManager>(),
      mock<SecretSession>()
    )
    val accountDefinitionRepository = SqlAccountDefinitionRepository(
      database.context,
      mapper,
      Clock.systemDefaultZone(),
      ConnectionPools.ACCOUNTS.value
    )
    val accountName = "testAccount"
    val testAccountOne = TestAccountOne(accountName)
    val testAccountTwo = TestAccountTwo(accountName)
  }

  @JsonTypeName("testTypeOne")
  private class TestAccountOne(private val name: String) : AccessControlledAccountDefinition {
    override fun getName(): String {
      return name
    }

    override fun getPermissions(): MutableMap<Authorization, MutableSet<String>> {
      return mutableMapOf()
    }
  }

  @JsonTypeName("testTypeTwo")
  private class TestAccountTwo(private val name: String) : AccessControlledAccountDefinition {
    override fun getName(): String {
      return name
    }

    override fun getPermissions(): MutableMap<Authorization, MutableSet<String>> {
      return mutableMapOf()
    }
  }
}
