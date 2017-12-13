package com.netflix.kayenta.atlas;

import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.atlas.model.AtlasResultsHelper;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AtlasResultsHelperTest {
  @Test
  public void multipleTagsTest() {
    Map<String, String> map1 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1.1")
      .put("tag2", "value2.1")
      .put("tag3", "value3")
      .build();
    Map<String, String> map2 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1.2")
      .put("tag2", "value2.2")
      .put("tag3", "value3")
      .build();
    Map<String, String> map3 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1.3")
      .put("tag2", "value2.3")
      .put("tag3", "value3")
      .build();
    List<Map<String, String>> input = Arrays.asList(map1, map2, map3);

    List<String> expected = Arrays.asList("tag1", "tag2");

    List<String> result = AtlasResultsHelper.interestingKeys(input);

    assertEquals(expected, result);
  }

  @Test
  public void noUniqueTagsTest() {
    Map<String, String> map1 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2")
      .put("tag3", "value3")
      .build();
    Map<String, String> map2 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2")
      .put("tag3", "value3")
      .build();
    Map<String, String> map3 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2")
      .put("tag3", "value3")
      .build();
    List<Map<String, String>> input = Arrays.asList(map1, map2, map3);

    List<String> expected = Collections.emptyList();

    List<String> result = AtlasResultsHelper.interestingKeys(input);

    assertEquals(expected, result);
  }

  @Test
  public void combinedTest() {
    Map<String, String> map1 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2")
      .build();
    Map<String, String> map2 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2.2")
      .build();
    Map<String, String> map3 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2.3")
      .put("tag3", "value3")
      .build();
    List<Map<String, String>> input = Arrays.asList(map1, map2, map3);

    List<String> expected = Collections.singletonList("tag2");

    List<String> result = AtlasResultsHelper.interestingKeys(input);

    assertEquals(expected, result);
  }

  @Test
  public void ignoresNullMapsTest() {
    Map<String, String> map1 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2")
      .build();
    Map<String, String> map3 = new ImmutableMap.Builder<String, String>()
      .put("tag1", "value1")
      .put("tag2", "value2.3")
      .put("tag3", "value3")
      .build();
    List<Map<String, String>> input = Arrays.asList(map1, null, map3);

    List<String> expected = Collections.singletonList("tag2");

    List<String> result = AtlasResultsHelper.interestingKeys(input);

    assertEquals(expected, result);
  }

}
