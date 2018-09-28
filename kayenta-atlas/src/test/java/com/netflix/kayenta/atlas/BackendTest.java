package com.netflix.kayenta.atlas;

import com.netflix.kayenta.atlas.model.Backend;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class BackendTest {
  @Test
  public void backendGetTargetsTestEnvDataset() {
    ArrayList regions = new ArrayList<>();
    regions.add("regionOne");
    regions.add("regionTwo");

    ArrayList environments = new ArrayList<>();
    environments.add("envOne");
    environments.add("envTwo");

    Backend backend = Backend.builder().target("$(env).$(region).$(dataset)").dataset("myDataset").deployment("myDeployment").environments(environments).regions(regions).build();
    List<String> targets = backend.getTargets();
    assertEquals(4, targets.size());
    assert(targets.contains("envOne.regionOne.myDataset"));
    assert(targets.contains("envTwo.regionOne.myDataset"));
    assert(targets.contains("envOne.regionTwo.myDataset"));
    assert(targets.contains("envTwo.regionTwo.myDataset"));
  }

  @Test
  public void backendGetTargetsTestNoEnvDataset() {
    ArrayList regions = new ArrayList<>();
    regions.add("regionOne");
    regions.add("regionTwo");

    Backend backend = Backend.builder().target("$(region).$(dataset)").dataset("myDataset").deployment("myDeployment").environments(null).regions(regions).build();
    List<String> targets = backend.getTargets();
    assertEquals(2, targets.size());
    assert(targets.contains("regionOne.myDataset"));
    assert(targets.contains("regionTwo.myDataset"));
  }

  @Test
  public void testGetUriForLocationWithRegionOnly() {
    ArrayList regions = new ArrayList<>();
    regions.add("regionOne");
    regions.add("regionTwo");

    Backend backend = Backend.builder()
      .target("$(region).$(dataset)")
      .cname("atlas.$(region)-$(dataset).example.com")
      .dataset("myDataset")
      .deployment("myDeployment")
      .environments(null)
      .regions(regions)
      .build();

    assertNull(backend.getUriForLocation("http", "notgonnabethere"));
    assertNull(backend.getUriForLocation("http", "regionNotThere.myDataset"));
    assertEquals("http://atlas.regionOne-myDataset.example.com", backend.getUriForLocation("http", "regionOne.myDataset"));
  }

  @Test
  public void testGetUriForLocationWithEnvironmentOnly() {
    ArrayList environments = new ArrayList<>();
    environments.add("test");
    environments.add("prod");

    Backend backend = Backend.builder()
      .target("$(env).$(dataset)")
      .cname("atlas.$(env)-$(dataset).example.com")
      .dataset("myDataset")
      .deployment("myDeployment")
      .environments(environments)
      .regions(Collections.emptyList())
      .build();

    assertNull(backend.getUriForLocation("http", "notgonnabethere"));
    assertNull(backend.getUriForLocation("http", "regionNotThere.myDataset"));
    assertEquals("http://atlas.test-myDataset.example.com", backend.getUriForLocation("http", "test.myDataset"));
  }

  @Test
  public void testGetUriForLocationWithBothEnvironmentsAndRegions() {
    ArrayList environments = new ArrayList<>();
    environments.add("test");
    environments.add("prod");

    ArrayList regions = new ArrayList<>();
    regions.add("regionOne");
    regions.add("regionTwo");

    Backend backend = Backend.builder()
      .target("$(region).$(env).$(dataset)")
      .cname("atlas.$(region).$(env).example.com")
      .dataset("myDataset")
      .deployment("myDeployment")
      .environments(environments)
      .regions(regions)
      .build();

    assertNull(backend.getUriForLocation("http", "notgonnabethere"));
    assertNull(backend.getUriForLocation("http", "regionNotThere.myDataset"));
    assertEquals("http://atlas.regionOne.test.example.com",
                 backend.getUriForLocation("http", "regionOne.test.myDataset"));
  }

  @Test
  public void testGetUriForLocationWithoutEnvironmentsOrRegions() {
    Backend backend = Backend.builder()
      .target("$(deployment).$(dataset)")
      .cname("atlas.$(dataset).$(deployment).example.com")
      .dataset("myDataset")
      .deployment("myDeployment")
      .environments(Collections.emptyList())
      .regions(Collections.emptyList())
      .build();

    assertNull(backend.getUriForLocation("http", "notgonnabethere"));
    assertNull(backend.getUriForLocation("http", "regionNotThere.myDataset"));
    assertEquals("http://atlas.myDataset.myDeployment.example.com",
                 backend.getUriForLocation("http", "myDeployment.myDataset"));
  }

}
