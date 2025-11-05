package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith({WiremockResolver.class})
class EcsImageFinderTest {

  @Test
  void findIMagePassesMap(@WiremockResolver.Wiremock WireMockServer server) {
    // At the moment, clouddriver doesn't have any ECS images, so this will
    // return whatever matches a URL on the resulting image query parameter.
    server.stubFor(
        any(urlPathEqualTo("/ecs/images/find"))
            .willReturn(
                aResponse().withBody("[{\"amis\":{},\"attributes\":{},\"imageName\":\"asdf\"}]")));
    EcsImageFinder finder = new EcsImageFinder();
    finder.oortService =
        new Retrofit.Builder()
            .baseUrl(server.baseUrl())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(OortService.class);
    finder.objectMapper = new ObjectMapper();
    Collection<ImageFinder.ImageDetails> imageDetails =
        finder.byTags(
            new StageExecutionImpl(), "asdf", Map.of("ignored", "ignored"), List.of("alsoIgnored"));
    assertThat(imageDetails).hasSize(1);
    assertThat(imageDetails.iterator().next().getImageName()).isEqualTo("asdf");
    //    OortService service = CloudDriverConfiguration.ClouddriverRetrofitBuilder.
  }
}
