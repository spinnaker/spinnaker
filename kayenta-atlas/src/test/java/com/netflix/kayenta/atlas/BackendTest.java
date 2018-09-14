package com.netflix.kayenta.atlas;

import com.netflix.kayenta.atlas.model.Backend;
import org.junit.Test;

import java.util.ArrayList;
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
}
