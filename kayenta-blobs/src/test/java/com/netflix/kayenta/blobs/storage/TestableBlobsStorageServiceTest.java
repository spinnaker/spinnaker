/*
 * Copyright 2019 Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.blobs.storage;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.azure.security.AzureCredentials;
import com.netflix.kayenta.azure.security.AzureNamedAccountCredentials;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.tngtech.java.junit.dataprovider.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.*;
import org.mockito.Mockito;

@RunWith(DataProviderRunner.class)
@Slf4j
public class TestableBlobsStorageServiceTest {

  private String rootFolder = "testRootFolder";
  private TestableBlobsStorageService testBlobsStorageService;
  private AccountCredentials accountCredentials;
  private AccountCredentialsRepository credentialsRepository;
  private CanaryConfigIndex mockedCanaryConfigIndex;

  @Before
  public void setUp() {
    List<String> testAccountNames =
        Arrays.asList("AzDev_Testing_Account_1", "AzDev_Testing_Account_2");
    String kayenataAccountName = "Kayenta_Account_1";
    String azureAccountName = "AzDev_Testing_Account_1";
    String accountAccessKey = "testAccessKey";

    AzureNamedAccountCredentials.AzureNamedAccountCredentialsBuilder credentialsBuilder =
        AzureNamedAccountCredentials.builder();
    credentialsBuilder.name(kayenataAccountName);
    credentialsBuilder.credentials(
        new AzureCredentials(azureAccountName, accountAccessKey, "core.windows.net"));
    credentialsBuilder.rootFolder(rootFolder);
    credentialsBuilder.azureContainer(null);
    accountCredentials = credentialsBuilder.build();

    credentialsRepository = new MapBackedAccountCredentialsRepository();
    credentialsRepository.save(kayenataAccountName, accountCredentials);

    ObjectMapper kayentaObjectMapper = new ObjectMapper();
    this.mockedCanaryConfigIndex = mock(CanaryConfigIndex.class);

    this.testBlobsStorageService =
        new TestableBlobsStorageService(
            testAccountNames, kayentaObjectMapper, credentialsRepository, mockedCanaryConfigIndex);
  }

  @After
  public void tearDown() {}

  @Test
  @UseDataProvider("servicesAccountDataset")
  public void servicesAccount(String accountName, boolean expected) {
    log.info(String.format("Running servicesAccountTest(%s)", accountName));
    Assert.assertEquals(expected, testBlobsStorageService.servicesAccount(accountName));
  }

  @Test
  @UseDataProvider("loadObjectDataset")
  public void loadObject(
      String accountName,
      ObjectType objectType,
      String testItemKey,
      List<String> applications,
      String exceptionKey) {
    try {
      log.info(
          String.format(
              "Running loadObjectTest with accountName(%s) and itemKey(%s) for application(%s)",
              accountName, testItemKey, applications));
      testBlobsStorageService.blobStored.put("exceptionKey", exceptionKey);
      testBlobsStorageService.blobStored.put("application", applications.get(0));

      CanaryConfig result =
          testBlobsStorageService.loadObject(accountName, objectType, testItemKey);
      Assert.assertEquals(applications.get(0), result.getApplications().get(0));
    } catch (IllegalArgumentException e) {
      Assert.assertEquals("Unable to resolve account " + accountName + ".", e.getMessage());
    } catch (NotFoundException e) {
      Assert.assertEquals(
          "Could not fetch items from Azure Cloud Storage: Item not found at "
              + rootFolder
              + "/canary_config/"
              + testItemKey,
          e.getMessage());
    } catch (IllegalStateException e) {
      Assert.assertEquals(
          "Unable to deserialize object (key: " + testItemKey + ")", e.getMessage());
    }
  }

  @Test
  @UseDataProvider("storeObjectDataset")
  public void storeObject(
      String accountName,
      ObjectType objectType,
      String canaryConfigName,
      String application,
      boolean isAnUpdate) {
    String testItemKey = "some(GUID)";

    when(mockedCanaryConfigIndex.getRedisTime()).thenReturn(1163643740L);
    when(mockedCanaryConfigIndex.getIdFromName(
            accountCredentials, canaryConfigName, Collections.singletonList(application)))
        .thenReturn(null);

    String fakeFileName = "canary_config.json";
    String fakeBlobName = keytoPath(rootFolder, objectType.getGroup(), testItemKey, fakeFileName);

    CanaryConfig.CanaryConfigBuilder canaryConfigBuilder = CanaryConfig.builder();
    canaryConfigBuilder.name(canaryConfigName).applications(Collections.singletonList(application));
    CanaryConfig canaryConfig = canaryConfigBuilder.build();

    try {
      log.info(String.format("Running storeObjectTest for (%s)", fakeBlobName));
      testBlobsStorageService.storeObject(
          accountName, objectType, testItemKey, canaryConfig, fakeFileName, isAnUpdate);
      HashMap<String, String> result = testBlobsStorageService.blobStored;
      Assert.assertEquals(fakeBlobName, result.get("blob"));
    } catch (IllegalArgumentException e) {
      Assert.assertEquals("Unable to resolve account " + accountName + ".", e.getMessage());
    }
  }

  @Test
  @UseDataProvider("deleteObjectDataset")
  public void deleteObject(String accountName, ObjectType objectType, String testItemKey) {

    String fakeBlobName =
        rootFolder + "/" + objectType.getGroup() + "/" + testItemKey + "/canary_test.json";
    when(mockedCanaryConfigIndex.getRedisTime()).thenReturn(1163643740L);

    HashMap<String, Object> map = new HashMap<>();
    List<String> applications = new ArrayList<>();
    applications.add("Test_App");
    map.put("name", fakeBlobName);
    map.put("applications", applications);

    when(mockedCanaryConfigIndex.getSummaryFromId(accountCredentials, testItemKey)).thenReturn(map);
    doNothing()
        .when(mockedCanaryConfigIndex)
        .finishPendingUpdate(Mockito.any(), Mockito.any(), Mockito.any());

    try {
      log.info(
          "Running deleteObjectTest for rootFolder/" + objectType.getGroup() + "/" + testItemKey);
      testBlobsStorageService.deleteObject(accountName, objectType, testItemKey);
      HashMap<String, String> result = testBlobsStorageService.blobStored;
      Assert.assertEquals("invoked", result.get(String.format("deleteIfexists(%s)", fakeBlobName)));
    } catch (IllegalArgumentException e) {
      Assert.assertEquals("Unable to resolve account " + accountName + ".", e.getMessage());
    }
  }

  @Test
  @UseDataProvider("listObjectKeysDataset")
  public void listObjectKeys(
      String accountName, ObjectType objectType, List<String> applications, boolean skipIndex) {

    try {
      log.info("Running listObjectKeysTest for rootFolder" + "/" + objectType.getGroup() + "/");
      List<Map<String, Object>> result =
          testBlobsStorageService.listObjectKeys(accountName, objectType, applications, skipIndex);
      if (objectType == ObjectType.CANARY_CONFIG) {
        Assert.assertEquals("canary_test", result.get(0).get("name"));
      } else {
        Assert.assertEquals(6, result.size());
      }
    } catch (IllegalArgumentException e) {
      Assert.assertEquals("Unable to resolve account " + accountName + ".", e.getMessage());
    }
  }

  @DataProvider
  public static Object[][] servicesAccountDataset() {
    return new Object[][] {
      {"AzDev_Testing_Account_1", true},
      {"AzDev_Testing_Account_2", true},
      {"AzDev_Testing_Account_3", false},
      {"AzDev_Testing_Account_4", false}
    };
  }

  @DataProvider
  public static Object[][] loadObjectDataset() {
    return new Object[][] {
      {
        "Kayenta_Account_1",
        ObjectType.CANARY_CONFIG,
        "some(GUID)",
        Collections.singletonList("app1"),
        "0"
      },
      {
        "Kayenta_Account_2",
        ObjectType.CANARY_CONFIG,
        "some(GUID",
        Collections.singletonList("app2"),
        "0"
      },
      {
        "Kayenta_Account_1",
        ObjectType.METRIC_SET_LIST,
        "some(GUID)",
        Collections.singletonList("app3"),
        "0"
      },
      {
        "Kayenta_Account_1",
        ObjectType.METRIC_SET_PAIR_LIST,
        "some(GUID)",
        Collections.singletonList("app4"),
        "0"
      },
      {
        "Kayenta_Account_1",
        ObjectType.CANARY_CONFIG,
        "fake(GUID)",
        Collections.singletonList("app5"),
        "1"
      },
      {
        "Kayenta_Account_1",
        ObjectType.CANARY_CONFIG,
        "some(GUID)",
        Collections.singletonList("app6"),
        "2"
      }
    };
  }

  @DataProvider
  public static Object[][] storeObjectDataset() {
    return new Object[][] {
      {"Kayenta_Account_1", ObjectType.CANARY_CONFIG, "Test_Canary", "Test_App", false},
      {"Kayenta_Account_2", ObjectType.CANARY_CONFIG, "Test_Canary", "Test_App", false},
      {"Kayenta_Account_1", ObjectType.METRIC_SET_LIST, "Test_Canary", "Test_App", false},
      {"Kayenta_Account_1", ObjectType.METRIC_SET_PAIR_LIST, "Test_Canary", "Test_App", false},
      {"Kayenta_Account_1", ObjectType.CANARY_CONFIG, "Test_Canary", "Test_App", true},
    };
  }

  @DataProvider
  public static Object[][] deleteObjectDataset() {
    return new Object[][] {
      {"Kayenta_Account_1", ObjectType.CANARY_CONFIG, "some(GUID)"},
      {"Kayenta_Account_2", ObjectType.CANARY_CONFIG, "some(GUID"},
      {"Kayenta_Account_1", ObjectType.METRIC_SET_LIST, "some(GUID)"},
      {"Kayenta_Account_1", ObjectType.METRIC_SET_PAIR_LIST, "some(GUID)"},
      {"Kayenta_Account_1", ObjectType.METRIC_SET_PAIR_LIST, "fake(GUID)"},
    };
  }

  @DataProvider
  public static Object[][] listObjectKeysDataset() {
    return new Object[][] {
      {"Kayenta_Account_1", ObjectType.CANARY_CONFIG, Collections.singletonList("Test_App"), true},
      {"Kayenta_Account_2", ObjectType.CANARY_CONFIG, Collections.singletonList("Test_App"), true},
      {
        "Kayenta_Account_1",
        ObjectType.METRIC_SET_LIST,
        Collections.singletonList("Test_App"),
        false
      },
      {
        "Kayenta_Account_1",
        ObjectType.METRIC_SET_PAIR_LIST,
        Collections.singletonList("Test_App"),
        true
      }
    };
  }

  private String keytoPath(
      String rootFolder, String daoTypeName, String objectKey, String filename) {
    return rootFolder + '/' + daoTypeName + '/' + objectKey + '/' + filename;
  }
}
