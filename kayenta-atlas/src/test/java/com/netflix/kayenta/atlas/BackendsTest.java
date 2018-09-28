package com.netflix.kayenta.atlas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.netflix.kayenta.atlas.backends.BackendDatabase;
import com.netflix.kayenta.atlas.model.Backend;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

@Configuration
@ComponentScan({"com.netflix.kayenta.retrofit.config"
})
class BackendsConfig {}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {BackendsConfig.class})
public class BackendsTest {
  @Autowired
  private ResourceLoader resourceLoader;

  @Autowired
  private ObjectMapper objectMapper;

  private String getFileContent(String filename) throws IOException {
    try (InputStream inputStream = resourceLoader.getResource("classpath:com/netflix/kayenta/atlas/" + filename).getInputStream()) {
      return IOUtils.toString(inputStream, Charsets.UTF_8.name());
    }
  }

  private List<Backend> readBackends(String filename) throws IOException {
    String contents = getFileContent(filename);
    TypeReference<List<Backend>> mapType = new TypeReference<List<Backend>>() {};
    return objectMapper.readValue(contents, mapType);
  }

  @Test
  public void backendTest() throws IOException {
    List<Backend> backends = readBackends("backends.json");

    BackendDatabase db = new BackendDatabase();
    db.update(backends);

    Optional<Backend> ret = db.getOne("example", "example", "us-east-1", "test");
    assertTrue(ret.isPresent());

    ret = db.getOne("example", "example", "xxx", "test");
    assertFalse(ret.isPresent());

    ret = db.getOne("main", "global", "xxx", "test");
    assertTrue(ret.isPresent());
    assertEquals("atlas-global.$(env).example.com", ret.get().getCname());
  }

  @Test
  public void getLocationsTest() throws IOException {
    List<Backend> backends = readBackends("backends-location-test.json");

    BackendDatabase db = new BackendDatabase();
    db.update(backends);

    List<String> locations = db.getLocations();
    assertEquals(10, locations.size());
    assert(locations.contains("test.global"));
    assert(locations.contains("prod.global"));
    assert(locations.contains("test.us-east-1"));
    assert(locations.contains("prod.us-east-1"));
    assert(locations.contains("test.us-west-2"));
    assert(locations.contains("prod.us-west-2"));
    assert(locations.contains("test.eu-west-1"));
    assert(locations.contains("prod.eu-west-1"));
  }

  @Test
  public void formattingReplacesAllTheThings() {
    Backend backend = Backend.builder()
      .cname("deployment=$(deployment).region=$(region).env=$(env).dataset=$(dataset).envAgain=$(env)")
      .build();
    assertEquals("http://deployment=xmain.region=xregion.env=xtest.dataset=xglobal.envAgain=xtest",
                 backend.getUri("http", "xmain", "xglobal", "xregion", "xtest"));
  }
  @Test
  public void getUriForlocationTest() throws IOException {
    List<Backend> backends = readBackends("backends-location-test.json");

    BackendDatabase db = new BackendDatabase();
    db.update(backends);

    assertEquals("http://atlas-global.test.example.com",
                 db.getUriForLocation("http", "test.global"));
    assertEquals("http://atlas-global.prod.example.com",
                 db.getUriForLocation("http", "prod.global"));
    assertEquals("http://atlas-main.us-east-1.prod.example.com",
                 db.getUriForLocation("http", "prod.us-east-1"));
  }

}
