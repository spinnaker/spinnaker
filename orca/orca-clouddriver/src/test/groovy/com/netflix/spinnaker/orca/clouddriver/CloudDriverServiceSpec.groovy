package com.netflix.spinnaker.orca.clouddriver

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CloudDriverServiceSpec extends Specification {

  OortService oortService = Mock()
  @Subject
  CloudDriverService cloudDriverService = new CloudDriverService(oortService, OrcaObjectMapper.instance)

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
        return Calls.response(ResponseBody.create("{\"name\": \" + ${serverGroupName} + \"}", MediaType.parse("application/json") ))
      }

      throw makeSpinnakerHttpException(statusCode)
    }

    optionalTargetServerGroup.isPresent() == shouldExist
    (thrownException != null) == shouldThrowException

    where:
    serverGroupName | statusCode || shouldExist || shouldThrowException
    "app-v001"      | 200        || true        || false
    "app-v002"      | 404        || false       || false
    "app-v003"      | 500        || false       || true       // a non-404 should just rethrow the exception
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://some-url";
    retrofit2.Response retrofit2Response =
        retrofit2.Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
