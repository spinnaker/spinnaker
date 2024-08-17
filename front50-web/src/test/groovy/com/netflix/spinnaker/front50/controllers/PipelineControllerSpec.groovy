package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.api.validator.PipelineValidator
import com.netflix.spinnaker.front50.api.validator.ValidatorErrors
import com.netflix.spinnaker.front50.config.controllers.PipelineControllerConfig
import com.netflix.spinnaker.front50.exceptions.DuplicateEntityException
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Unroll
import spock.mock.DetachedMockFactory

import javax.servlet.http.HttpServletResponse
import java.util.concurrent.Executors
import java.util.stream.Collectors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = [PipelineController])
@ContextConfiguration(classes = [TestConfiguration, AuthorizationSupport, PipelineController, PipelineControllerConfig])
class PipelineControllerSpec extends Specification {

  @Autowired
  private MockMvc mockMvc

  @Autowired
  PipelineControllerConfig pipelineControllerConfig

  @Autowired
  FiatPermissionEvaluator fiatPermissionEvaluator

  @Autowired
  private AuthorizationSupport authorizationSupport

  @Unroll
  def "should fail the pipeline when staleCheck is true and conditions are met"() {
    given:
    def staleCheck_true = true
    def staleCheck_false = false
    def localFiatPermissionEvaluator = Mock(FiatPermissionEvaluator)
    def pipelinesBatch1 = [
      [      id          : "1",
             name        : "test-pipeline",
             application : "test-application",
             lastModified: 1662644108666
      ]
    ]

    def pipelinesBatch2 = [
      [      id          : "1",
             name        : "test-pipeline",
             application : "test-application",
      ]
    ]
    def pipelineDAO = new InMemoryPipelineDAO(){
      @Override
      Pipeline findById(String id) throws NotFoundException {
        return new Pipeline([
          id          : "1",
          name        : "test-pipeline",
          application : "test-application",
          lastModified: 1772644108777
        ])
      }

      @Override
      void bulkImport(Collection<Pipeline> items) {}
    }

    _ * localFiatPermissionEvaluator.hasPermission(_, "test-application", "APPLICATION", "WRITE") >> true

    def pipelineController = new PipelineController(
      pipelineDAO, new ObjectMapper(), Optional.empty(), [], Optional.empty(), pipelineControllerConfig,
      localFiatPermissionEvaluator, authorizationSupport)

    when: "staleCheck is true and conditions are met"
    def response = pipelineController.batchUpdate(pipelinesBatch1, staleCheck_true)

    then: "pipeline save should fail"
    response.failed_pipelines_count == 1
    response.successful_pipelines_count == 0
    response.successful_pipelines == []
    response.failed_pipelines[0].any.errorMsg ==
      "Encountered the following error when validating pipeline test-pipeline in the application test-application: " +
      "The submitted pipeline is stale.  submitted updateTs 1662644108666 does not match stored updateTs 1772644108777"

    when: "staleCheck is false"
    response = pipelineController.batchUpdate(pipelinesBatch1, staleCheck_false)

    then: "pipeline save should be successful"
    response.failed_pipelines_count == 0
    response.successful_pipelines_count == 1

    when: "staleCheck is true but submitted pipeline doesn't have lastModified timestamp"
    response = pipelineController.batchUpdate(pipelinesBatch2, staleCheck_true)

    then: "pipeline save should be successful"
    response.failed_pipelines_count == 0
    response.successful_pipelines_count == 1

  }

  @Unroll
  def "test cache refresh enabled flag when checking for duplicate pipelines"() {
    given:
    def existingPipelineIdNotInCache = "123"
    def newPipelineId = "456"
    def application = "test-application"
    def pipelineName = "test-pipeline"
    def pipeline = new Pipeline([
      id          : existingPipelineIdNotInCache,
      name        : pipelineName,
      application : application,
    ])

    def pipelineDAO = new InMemoryPipelineDAO(){
      @Override
      Collection<Pipeline> getPipelinesByApplication(String app, boolean refresh) {
        return refresh ? [pipeline] : []
      }
    }

    def pipelineController = new PipelineController(
      pipelineDAO, new ObjectMapper(), Optional.empty(), [], Optional.empty(), pipelineControllerConfig,
      fiatPermissionEvaluator, authorizationSupport)

    when:
    pipelineControllerConfig.getSave().refreshCacheOnDuplicatesCheck = true
    pipelineController.checkForDuplicatePipeline(application, pipelineName, newPipelineId)

    then:
    thrown DuplicateEntityException

    when:
    pipelineControllerConfig.getSave().refreshCacheOnDuplicatesCheck = false
    pipelineController.checkForDuplicatePipeline(application, pipelineName, newPipelineId)

    then:
    noExceptionThrown()

  }

  @Unroll
  def "should fail to save if application is missing, empty or blank"() {
    given:
    Map pipelineData = [
      name       : "some pipeline with no application",
      application: application
    ]

    when:
    HttpServletResponse response = mockMvc
      .perform(
        post("/pipelines")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelineData))
      )
      .andReturn()
      .response

    then:
    response.status == HttpStatus.UNPROCESSABLE_ENTITY.value()

    where:
    application << [null, "", "      "]
  }

  @Unroll
  def "should fail to save if pipeline name is missing, empty or blank"() {
    given:
    Map pipelineData = [
      name       : name,
      application: "some application"
    ]

    when:
    HttpServletResponse response = mockMvc
      .perform(
        post("/pipelines")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(pipelineData))
      )
      .andReturn()
      .response

    then:
    response.status == HttpStatus.UNPROCESSABLE_ENTITY.value()

    where:
    name << [null, "", "      "]
  }

  @Unroll
  def "should fail if validator errors are produced"() {

    given:
    Map newPipeline = [
      name       : "should-fail",
      application: "fail-app",
    ]

    def mockPipeline = new Pipeline([
      id         : "1",
      name       : "mock-pipeline",
      application: "test-app",
    ])
    def pipelineDAO = Stub(PipelineDAO.class)
    def pipelineList = mockPipeline as List<Pipeline>
    pipelineDAO.getPipelinesByApplication(any() as String) >> pipelineList

    def mockMvcWithValidator = MockMvcBuilders
      .standaloneSetup(
        new PipelineController(
          pipelineDAO,
          new ObjectMapper(),
          Optional.empty(),
          [new MockValidator()] as List<PipelineValidator>,
          Optional.empty(),
          pipelineControllerConfig,
          fiatPermissionEvaluator,
          authorizationSupport
        )
      )
      .setControllerAdvice(
        new GenericExceptionHandlers(
          new ExceptionMessageDecorator(Mock(ObjectProvider))
        )
      )
      .build()

    when:
    HttpServletResponse response = mockMvcWithValidator
      .perform(
        post("/pipelines")
          .contentType(MediaType.APPLICATION_JSON)
          .content(new ObjectMapper().writeValueAsString(newPipeline))
      )
      .andReturn()
      .response

    then:
    response.status == HttpStatus.BAD_REQUEST.value()
    response.errorMessage == "mock validator rejection 1\nmock validator rejection 2"
  }

  @Unroll
  def "should show updateTs field in the response"() {
    given:
    def testPipelineId = "123"
    def pipelineDAO = Stub(PipelineDAO.class)

    def pipeline = new Pipeline([
      id          : testPipelineId,
      name        : "test-pipeline",
      application : "test-application",
      lastModified: 1662644108709
    ])

    def pipelineList = [pipeline] as Collection<Pipeline>
    pipelineDAO.history(testPipelineId, 20) >> pipelineList

    def mockMvcWithController = MockMvcBuilders.standaloneSetup(new PipelineController(
      pipelineDAO, new ObjectMapper(), Optional.empty(), [], Optional.empty(), pipelineControllerConfig,
      fiatPermissionEvaluator, authorizationSupport
    )).build()

    when:
    HttpServletResponse response = mockMvcWithController
      .perform(get("/pipelines/$testPipelineId/history"))
      .andReturn()
      .response

    then:
    System.out.println(response.contentAsString)
    response.status == HttpStatus.OK.value()
    response.contentAsString.contains("\"id\":\"123\"")
    response.contentAsString.contains("\"name\":\"test-pipeline\"")
    response.contentAsString.contains("\"application\":\"test-application\"")
    response.contentAsString.contains("\"updateTs\":\"1662644108709\"")
  }

  @Unroll
  def "should create pipelines in a thread safe way"() {
    given:
    def pipelineDAO = new InMemoryPipelineDAO()
    def createPipelineFirstRequest = post("/pipelines")
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString([
        id         : "1",
        name       : "pipeline-name",
        application: "application-name",
      ]))
    def createPipelineSecondRequest = post("/pipelines")
      .contentType(MediaType.APPLICATION_JSON)
      .content(new ObjectMapper().writeValueAsString([
        id         : "2",
        name       : "pipeline-name",
        application: "application-name",
      ]))

    def mockMvcWithController = MockMvcBuilders.standaloneSetup(new PipelineController(
      pipelineDAO, new ObjectMapper(), Optional.empty(), [], Optional.empty(), pipelineControllerConfig,
      fiatPermissionEvaluator, authorizationSupport
    )).build()

    when:

    def all = Executors.newFixedThreadPool(2).invokeAll(List.of(
      { -> mockMvcWithController.perform(createPipelineFirstRequest).andReturn().response },
      { -> mockMvcWithController.perform(createPipelineSecondRequest).andReturn().response },
    ))
    then:
    (all.get(0).get().status == 200 && all.get(1).get().status == 400) || (all.get(0).get().status == 400 && all.get(1).get().status == 200)
  }

  @Configuration
  private static class TestConfiguration {
    DetachedMockFactory detachedMockFactory = new DetachedMockFactory()

    @Bean
    PipelineDAO pipelineDAO() {
      detachedMockFactory.Stub(PipelineDAO)
    }

    @Bean
    FiatPermissionEvaluator fiatPermissionEvaluator() {
      detachedMockFactory.Stub(FiatPermissionEvaluator)
    }
  }

  private class MockValidator implements PipelineValidator {
    @Override
    void validate(Pipeline pipeline, ValidatorErrors errors) {
      if (pipeline.getName() == "should-fail") {
        errors.reject("mock validator rejection 1")
        errors.reject("mock validator rejection 2")
      }
    }
  }


  private class InMemoryPipelineDAO implements PipelineDAO {

    private final Map<String, Pipeline> map = new HashMap<>()

    @Override
    String getPipelineId(String application, String pipelineName) {
      map.values().stream()
        .filter({ p -> p.getApplication().equalsIgnoreCase(application) })
        .filter({ p -> p.getName().equalsIgnoreCase(pipelineName) })
        .collect(Collectors.toList())
    }

    @Override
    Collection<Pipeline> getPipelinesByApplication(String application) {
      map.values().stream()
        .filter({ p -> p.getApplication().equalsIgnoreCase(application) })
        .collect(Collectors.toList())
    }

    @Override
    Collection<Pipeline> getPipelinesByApplication(String application, boolean refresh) {
      map.values().stream()
        .filter({ p -> p.getApplication().equalsIgnoreCase(application) })
        .collect(Collectors.toList())
    }

    @Override
    Pipeline getPipelineByName(String application, String pipelineName, boolean refresh) {
      map.values().stream()
        .filter({ p -> p.getApplication().equalsIgnoreCase(application) })
        .filter({ p -> p.getName().equalsIgnoreCase(pipelineName) })
        .collect(Collectors.toList())
    }

    @Override
    Pipeline findById(String id) throws NotFoundException {
      def get = map.get(id)
      if (get == null) {
        throw new NotFoundException("Didn't find Pipeline with id: " + id)
      }
      return get
    }

    @Override
    Collection<Pipeline> all() {
      return map.values()
    }

    @Override
    Collection<Pipeline> all(boolean refresh) {
      return map.values()
    }

    @Override
    Collection<Pipeline> history(String id, int maxResults) {
      throw new UnsupportedOperationException()
    }

    @Override
    Pipeline create(String id, Pipeline item) {
      map.put(id, item)
      item
    }

    @Override
    void update(String id, Pipeline item) {
      map.put(id, item)
    }

    @Override
    void delete(String id) {
      map.remove(id)
    }

    @Override
    void bulkImport(Collection<Pipeline> items) {
      throw new UnsupportedOperationException()
    }

    @Override
    boolean isHealthy() {
      return true
    }
  }

}
