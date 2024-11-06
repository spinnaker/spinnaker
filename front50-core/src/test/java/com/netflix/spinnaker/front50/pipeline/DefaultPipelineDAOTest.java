/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.front50.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

public abstract class DefaultPipelineDAOTest extends PipelineDAOSpec<DefaultPipelineDAO> {

  protected DefaultPipelineDAO pipelineDAO;

  @Override
  public DefaultPipelineDAO getInstance() {
    return getDefaultPipelineDAO();
  }

  public abstract DefaultPipelineDAO getDefaultPipelineDAO();

  @BeforeEach
  public void setup() {
    this.pipelineDAO = Mockito.spy(getDefaultPipelineDAO());
  }

  @ParameterizedTest
  @CsvSource({
    "'app', 'pipelineNameA', 'NameA', 'pipelineNameA'",
    "'app', 'pipelineNameA', , 'pipelineNameA'",
    "'app', , , "
  })
  public void shouldReturnCorrectPipelinesWhenRequestingPipelinesByApplicationWithNameFilter(
      String applicationName,
      String pipelineName,
      String pipelineNameFilter,
      String expectedPipelineName) {

    Pipeline pipeline = new Pipeline();
    pipeline.setId("0");
    pipeline.setApplication(applicationName);
    pipeline.setName(pipelineName);

    doReturn(List.of(pipeline)).when(pipelineDAO).all(anyBoolean());

    Collection<Pipeline> pipelines =
        pipelineDAO.getPipelinesByApplication("app", pipelineNameFilter, true);

    Pipeline resultPipeline = pipelines.iterator().next();
    assertEquals(resultPipeline.getName(), expectedPipelineName);
    assertEquals(resultPipeline.getApplication(), "app");
  }

  @ParameterizedTest
  @CsvSource({
    "'app', , 'NameA'",
    "'bad', 'pipelineNameA', 'NameA'",
    "'bad', , 'NameA'",
    "'bad', 'pipelineNameA', ",
    "'bad', , "
  })
  public void shouldReturnNoPipelinesWhenRequestingPipelinesByApplicationWithNameFilter(
      String applicationName, String pipelineName, String pipelineNameFilter) {

    Pipeline pipeline = new Pipeline();
    pipeline.setId("0");
    pipeline.setApplication(applicationName);
    pipeline.setName(pipelineName);

    doReturn(List.of(pipeline)).when(pipelineDAO).all(true);

    Collection<Pipeline> pipelines =
        pipelineDAO.getPipelinesByApplication("app", pipelineNameFilter, true);

    assertEquals(0, pipelines.size());
  }
}

class SqlDefaultPipelineDAOTest extends DefaultPipelineDAOTest {

  private SqlTestUtil.TestDatabase database = SqlTestUtil.initTcMysqlDatabase();

  @AfterEach
  public void cleanup() {
    if (database != null) {
      SqlTestUtil.cleanupDb(database.context);
      database.close();
    }
  }

  @Override
  public DefaultPipelineDAO getDefaultPipelineDAO() {
    return SqlPipelineDAOTestConfiguration.createPipelineDAO(database);
  }
}
