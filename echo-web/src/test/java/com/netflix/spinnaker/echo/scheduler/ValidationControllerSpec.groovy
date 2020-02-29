package  com.netflix.spinnaker.echo.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import org.springframework.test.web.servlet.MvcResult
import spock.lang.Shared
import spock.lang.Specification
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ValidationControllerSpec extends Specification {

  @Shared ObjectMapper objectMapper = EchoObjectMapper.getInstance()

  @Shared def mvc = MockMvcBuilders.standaloneSetup(new ValidationController()).build()

  MvcResult validate(String expression) {
    mvc.perform(MockMvcRequestBuilders
        .get("/validateCronExpression")
        .param("cronExpression", expression)).andReturn()
  }

  void 'should include description when expression is valid'() {
    when:
    def result = validate("0 0 10 ? * THU")

    def responseBody = objectMapper.readValue(result.response.contentAsByteArray, Map)

    then:
    responseBody.response == "Cron expression is valid"
    responseBody.description == "At 10:00 AM, only on Thursday"
  }

  void 'should include failure message when expression is invalid'() {
    when:
    def result = validate("0 0 10 * * 1")

    then:
    result.response.status == 400
    result.response.errorMessage == "Invalid CRON expression: '0 0 10 * * 1' explanation: Support for specifying both a day-of-week AND a day-of-month parameter is not implemented."
  }

  void 'should validate fuzzy expressions'() {
    when:
    def result = validate("H 0 10 ? * 1")
    def responseBody = objectMapper.readValue(result.response.contentAsByteArray, Map)

    then:
    responseBody.response == "Cron expression is valid"
    responseBody.description == "No description available for fuzzy cron expressions"

  }
}
