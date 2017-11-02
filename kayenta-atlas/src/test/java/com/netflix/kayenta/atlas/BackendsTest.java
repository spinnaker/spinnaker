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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Configuration
@ComponentScan({
  "com.netflix.kayenta.retrofit.config"
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
  public void formattingReplacesDeploymentAndOmitsPort80() {
    Backend backend = Backend.builder()
      .cname("deployment=$(deployment)")
      .port(80)
      .build();
    assertEquals("http://deployment=main",
                 backend.getUri("http", "main", "global", "region", "test"));
    assertEquals("https://deployment=main:80",
                 backend.getUri("https", "main", "global", "region", "test"));
  }

  @Test
  public void formattingReplacesDeploymentAndOmitsPort443() {
    Backend backend = Backend.builder()
      .cname("deployment=$(deployment)")
      .port(443)
      .build();
    assertEquals("http://deployment=main:443",
                 backend.getUri("http", "main", "global", "region", "test"));
    assertEquals("https://deployment=main",
                 backend.getUri("https", "main", "global", "region", "test"));
  }

  @Test
  public void formattingReplacesDeploymentAndIncludesPort() {
    Backend backend = Backend.builder()
      .cname("deployment=$(deployment)")
      .port(8000)
      .build();
    assertEquals("http://deployment=main:8000",
                 backend.getUri("http", "main", "global", "region", "test"));
    assertEquals("https://deployment=main:8000",
                 backend.getUri("https", "main", "global", "region", "test"));
  }

  @Test
  public void formattingReplacesAllThethings() {
    Backend backend = Backend.builder()
      .cname("deployment=$(deployment).region=$(region).env=$(env).dataset=$(dataset).envAgain=$(env)")
      .port(80)
      .build();
    assertEquals("http://deployment=xmain.region=xregion.env=xtest.dataset=xglobal.envAgain=xtest",
                 backend.getUri("http", "xmain", "xglobal", "xregion", "xtest"));
  }
}
