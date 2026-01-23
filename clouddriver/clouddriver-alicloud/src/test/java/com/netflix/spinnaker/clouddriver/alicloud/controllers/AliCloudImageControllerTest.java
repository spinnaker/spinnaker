/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.alicloud.controllers;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.alicloud.controllers.AliCloudImageController.Image;
import com.netflix.spinnaker.clouddriver.alicloud.controllers.AliCloudImageController.LookupOptions;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudImageControllerTest {

  private final String ACCOUNT = "test-account";
  private final String REGION = "cn-test";

  final Cache cacheView = mock(Cache.class);
  final LookupOptions lookupOptions = mock(LookupOptions.class);
  final HttpServletRequest request = mock(HttpServletRequest.class);

  @Before
  public void testBefore() {
    when(cacheView.filterIdentifiers(anyString(), anyString())).thenAnswer(new FilterAnswer());
    when(cacheView.getAll(anyString(), any(), any())).thenAnswer(new CacheDataAnswer());
    when(lookupOptions.getQ()).thenReturn("test");
    when(request.getParameterNames()).thenAnswer(new RequestAnswer());
  }

  @Test
  public void testList() {
    AliCloudImageController controller = new AliCloudImageController(cacheView);
    List<Image> list = controller.list(lookupOptions, request);
    assertTrue(list.size() == 2);
  }

  private class FilterAnswer implements Answer<List<String>> {
    @Override
    public List<String> answer(InvocationOnMock invocation) throws Throwable {
      List<String> list = new ArrayList<>();
      list.add("alicloud:images:ali-account:cn-hangzhou:win_xxx_xxx_xxx.vhd");
      return list;
    }
  }

  private class CacheDataAnswer implements Answer<List<CacheData>> {
    @Override
    public List<CacheData> answer(InvocationOnMock invocation) throws Throwable {
      List<CacheData> cacheDatas = new ArrayList<>();
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("account", ACCOUNT);
      attributes.put("regionId", REGION);
      attributes.put("imageName", "win_xxx_xxx_xxx.vhd");
      CacheData cacheData1 =
          new DefaultCacheData(
              "alicloud:images:ali-account:cn-hangzhou:win_xxx_xxx_xxx.vhd", attributes, null);
      cacheDatas.add(cacheData1);
      return cacheDatas;
    }
  }

  private class RequestAnswer implements Answer<Enumeration<String>> {
    @Override
    public Enumeration<String> answer(InvocationOnMock invocation) throws Throwable {
      List<String> list = new ArrayList<>();
      Enumeration<String> enumeration = Collections.enumeration(list);
      return enumeration;
    }
  }
}
