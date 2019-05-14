package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class RouteTest {
  @Test
  void routeSerialization() throws IOException {
    RouteId routeId = new RouteId("host", "path", 8080, "domainId");
    Route route = new Route(routeId, "spaceId");

    String routeSerialized =
        new ObjectMapper()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .writeValueAsString(route);
    assertThat(routeSerialized)
        .isEqualTo(
            "{\"domainGuid\":\"domainId\",\"host\":\"host\",\"path\":\"path\",\"port\":8080,\"spaceGuid\":\"spaceId\"}");
    assertThat(new ObjectMapper().readValue(routeSerialized, Route.class).getRouteId())
        .isEqualTo(routeId);
  }
}
