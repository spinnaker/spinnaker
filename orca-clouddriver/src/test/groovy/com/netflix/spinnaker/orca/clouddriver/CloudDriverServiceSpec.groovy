package com.netflix.spinnaker.orca.clouddriver


import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CloudDriverServiceSpec extends Specification {

  OortService oortService = Mock()
  @Subject CloudDriverService cloudDriverService = new CloudDriverService(oortService, OrcaObjectMapper.instance)

  @Unroll
  def "should support fetching a target server group that does not exist"() {
    when:
    def optionalTargetServerGroup = Optional.empty()
    def thrownException

    try {
      optionalTargetServerGroup = cloudDriverService.getTargetServerGroup("test", serverGroupName, "us-west-2")
    } catch (Exception e) {
      thrownException = e
    }

    then:
    1 * oortService.getServerGroup("test", "us-west-2", serverGroupName) >> {
      if (statusCode == 200) {
        return new Response("http://clouddriver", statusCode, "OK", [], new TypedString("""{"name": "${serverGroupName}"}"""))
      }

      throw RetrofitError.httpError(
          null,
          new Response("http://clouddriver", statusCode, "", [], null),
          null,
          null
      )
    }

    optionalTargetServerGroup.isPresent() == shouldExist
    (thrownException != null) == shouldThrowException

    where:
    serverGroupName | statusCode || shouldExist || shouldThrowException
    "app-v001"      | 200        || true        || false
    "app-v002"      | 404        || false       || false
    "app-v003"      | 500        || false       || true       // a non-404 should just rethrow the exception
  }
}
