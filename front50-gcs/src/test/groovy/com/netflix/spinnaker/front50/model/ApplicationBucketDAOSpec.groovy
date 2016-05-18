/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.model

import com.netflix.spinnaker.front50.model.application.Application;

import rx.Scheduler;
import rx.schedulers.Schedulers;
import spock.lang.Shared
import spock.lang.Specification
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.stream.Collectors;

class ApplicationBucketDAOSpec extends Specification {
  class TestDAO extends ApplicationBucketDAO {
    TestDAO(String rootFolder, StorageService service, Scheduler scheduler, int refreshMs) {
      super(rootFolder, service, scheduler, refreshMs);
    }

    public Collection<Application> cache() { return allItemsCache.get().stream().collect(Collectors.toSet()) }
    public void setCache(Set<Application> items) { allItemsCache.set(items) }
  }

  @Shared
  int REFRESH_MS = 10000  // dont refresh during our tests

  @Shared
  String rootFolder = "A/B/C"

  Application mockApp = Mock(Application)
  StorageService mockService = Mock(StorageService)
  Scheduler realScheduler = Schedulers.from(Executors.newFixedThreadPool(5))

  TestDAO dao

  void setup() {
    dao = new TestDAO(rootFolder, mockService, realScheduler, REFRESH_MS)
  }

  void "findById"() {
    when:
      Application app = dao.findById('TestId')

    then:
      1 * mockService.loadCurrentObject(
              "testid", "applications", Application.class) >> mockApp
      app == mockApp
  }

  void "findById ignores cache"() {
    Application no = Mock(Application)
    Application yes = Mock(Application)
    dao.setCache([yes, no] as Set)

    when:
      Application app = dao.findById('TestId')

    then:
      1 * mockService.loadCurrentObject(
              "testid", "applications", Application.class) >> mockApp
      app == mockApp
  }

  void "startRefresh warms cache"() {
    dao = new TestDAO(rootFolder, mockService, realScheduler, 2000)

    when:
      dao.startRefresh();
      System.sleep(50);
    then:
      1 * mockService.listObjectKeys("applications") >> new HashMap()
  }

  void "startRefresh will poll"() {
    dao = new TestDAO(rootFolder, mockService, realScheduler, 200)
    long start = System.currentTimeMillis()

    when:
      dao.startRefresh();
      System.sleep(500);
    then:
      3 * mockService.listObjectKeys("applications") >> new HashMap()
  }

  void "update does not affect cache"() {
    Application old = Mock(Application)
    Application update = Mock(Application)
    dao.setCache([old] as Set)

    when:
     dao.update("New", update)

    then:
     1 * mockService.storeObject("new", "applications", update)
     dao.cache() == [old] as Set
  }

  void "delete does not affect cache"() {
    Application keep = Mock(Application)
    Application remove = Mock(Application)
    dao.setCache([keep, remove] as Set)

    when:
    dao.delete("Remove")

    then:
    1 * mockService.deleteObject("remove", "applications")
    dao.cache() == [keep, remove] as Set
  }

  void "all empty"() {
    Map emptyMap = [:]

    when:
      dao.all();
    then:
      1 * mockService.getLastModified("applications") >> 0
      1 * mockService.listObjectKeys("applications") >> emptyMap
  }

  void "all with two"() {
    Map twoMap = ["abc":123, "xyz":987]
    Application abc = Mock(Application)
    Application xyz = Mock(Application)

    when:
      Collection<Application> result = dao.all();
    then:
      1 * mockService.getLastModified("applications") >> 1
      1 * mockService.listObjectKeys("applications") >> twoMap
      1 * mockService.loadCurrentObject("abc", "applications", Application.class) >> abc
      1 * abc.getId() >> "abc"
      1 * mockService.loadCurrentObject("xyz", "applications", Application.class) >> xyz
      1 * xyz.getId() >> "XYZ"
      result.sort() == [abc, xyz].sort()
      dao.cache() == [abc, xyz] as Set
  }

  void "all fresh cache"() {
    Application abc = Mock(Application)
    Application xyz = Mock(Application)
    dao.setCache([abc, xyz] as Set)

    when:
    Collection<Application> result = dao.all();

    then:
    1 * mockService.getLastModified("applications") >> 0
    result.sort() == [abc, xyz].sort()
  }

  void "all stale cache"() {
    long modTime = 12345L
    Application oldFresh = Mock(Application)
    Application oldGone = Mock(Application)
    Application oldStale = Mock(Application)
    Application newStale = Mock(Application)
    Application newNew = Mock(Application)

    Map foundMap = ["fresh":123L, "stale":modTime, "new":modTime - 1]
    dao.setCache([oldFresh, oldStale, oldGone] as Set)

    when:
    Collection<Application> result = dao.all();

    then:
    1 * mockService.getLastModified("applications") >> modTime
    1 * oldFresh.getId() >> "fresh"
    1 * oldStale.getId() >> "stale"
    1 * oldGone.getId() >> "gone"

    then:
    1 * mockService.listObjectKeys("applications") >> foundMap
    2 * oldFresh.getId() >> "fresh"  // lookup in filter, insert into map
    2 * oldStale.getId() >> "stale"  // lookup in filter, insert into map
    1 * oldGone.getId() >> "gone"    // lookup in filter
    1 * oldFresh.getLastModified() >> 123L  // same as our scan
    1 * oldStale.getLastModified() >> 123L  // older than our scan

    then:
    1 * mockService.loadCurrentObject("stale", "applications", Application.class) >> newStale
    1 * newStale.getId() >> "stale"
    1 * mockService.loadCurrentObject("new", "applications", Application.class) >> newNew
    1 * newNew.getId() >> "new"
    _ * oldFresh.hashCode() >> 123
    _ * newStale.hashCode() >> 234
    _ * mockService.toString()
    _ * oldFresh.toString()
    _ * oldGone.toString()
    _ * oldStale.toString()
    _ * newStale.toString()

    result.sort() == [oldFresh, newNew, newStale].sort()
    dao.cache() == result.toSet()
  }
}
