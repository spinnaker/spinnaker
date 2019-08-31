/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.bakery

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.api.BaseImage
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest
import com.netflix.spinnaker.orca.bakery.api.manifests.helm.HelmBakeManifestRequest
import com.netflix.spinnaker.orca.bakery.config.BakeryConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.model.Execution
import retrofit.http.Body
import retrofit.http.Path
import retrofit.http.Query
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.kork.web.selector.v2.SelectableService.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class BakerySelectorSpec extends Specification {
  def bakeryConfigProperties = new BakeryConfigurationProperties(
    baseUrl: "http://bakery.com",
    baseUrls: [
      new BaseUrl(
        baseUrl: "http://rosco.us-east-1.com",
        priority: 1,
        config: [:],
        parameters: [
          new Parameter(name: "region", values: ["us-east-1"]),
          new Parameter(name: "cloudProviderType", values: ["aws"])
        ]
      ),
      new BaseUrl(
        baseUrl: "http://rosco.us-west-2.com",
        priority: 2,
        config: [:],
        parameters: [
          new Parameter(name: "region", values: ["us-west-2"])
        ]
      ),
      new BaseUrl(
        baseUrl: "http://rosco.eu-west-1.com",
        priority: 4,
        config: [:],
        parameters: [
          new Parameter(name: "authenticatedUser", values: ["regex:^[a..b].+@netflix.com"]),
        ]
      )
    ]
  )

  @Subject
  def bakerySelector = new BakerySelector(
    new TestBakeryService(url: bakeryConfigProperties.baseUrl),
    bakeryConfigProperties,
    { url -> new TestBakeryService(url: url) }
  )

  @Unroll
  def "should select bakery by context"() {
    given:
    def bakePipeline = pipeline {
      application: "foo"
      authentication = new Execution.AuthenticationDetails(user: user)
      stage {
        type = "bake"
        context = ctx as Map
      }
    }

    when:
    def result = bakerySelector.select(bakePipeline.stages.first())

    then:
    result.service == service

    where:
    ctx                                       | user               || service
    [region: "us-east-1"]                     | "user@netflix.com" || new TestBakeryService(url: "http://bakery.com")
    [region: "us-east-1", cloudProviderType: "aws", selectBakery: true] | "user@netflix.com" || new TestBakeryService(url: "http://rosco.us-east-1.com")
    [region: "eu-west-1", selectBakery: true] | "test@netflix.com" || new TestBakeryService(url: "http://bakery.com")
    [selectBakery: true]                      | "bob@netflix.com"  || new TestBakeryService(url: "http://rosco.eu-west-1.com")
    [selectBakery: false]                     | "bob@netflix.com"  || new TestBakeryService(url: "http://bakery.com")
  }

  private static class TestBakeryService implements BakeryService {
    private String url

    boolean equals(o) {
      if (this.is(o)) {
        return true
      }
      if (getClass() != o.class) {
        return false
      }

      TestBakeryService that = (TestBakeryService) o
      if (url != that.url) {
        return false
      }
      return true
    }

    int hashCode() {
      return (url != null ? url.hashCode() : 0)
    }

    @Override
    Artifact bakeManifest(@Path("type") String type, @Body BakeManifestRequest bakeRequest) {
      return null
    }

    @Override
    Observable<BakeStatus> createBake(@Path("region") String region, @Body BakeRequest bake, @Query("rebake") String rebake) {
      return null
    }

    @Override
    Observable<BakeStatus> lookupStatus(@Path("region") String region, @Path("statusId") String statusId) {
      return null
    }

    @Override
    Observable<Bake> lookupBake(@Path("region") String region, @Path("bakeId") String bakeId) {
      return null
    }

    @Override
    Observable<BaseImage> getBaseImage(@Path("cloudProvider") String cloudProvider, @Path("imageId") String imageId) {
      return null
    }
  }
}
