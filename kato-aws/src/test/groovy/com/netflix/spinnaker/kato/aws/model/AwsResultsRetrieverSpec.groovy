/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.aws.model

import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult
import com.amazonaws.services.ec2.model.SpotPrice
import spock.lang.Specification

class AwsResultsRetrieverSpec  extends Specification {

  def service = Mock(AwsEc2Service)

  def retriever = new AwsResultsRetriever<SpotPrice, DescribeSpotPriceHistoryRequest, DescribeSpotPriceHistoryResult>() {
    DescribeSpotPriceHistoryResult makeRequest(DescribeSpotPriceHistoryRequest request) {
      service.describeSpotPriceHistory(request)
    }
    List<SpotPrice> accessResult(DescribeSpotPriceHistoryResult result) {
      result.spotPriceHistory
    }
  }

  static interface AwsEc2Service {
    DescribeSpotPriceHistoryResult describeSpotPriceHistory(DescribeSpotPriceHistoryRequest request)
  }

  def 'should retrieve for all tokens'() {
    when:
    List<SpotPrice> actual = retriever.retrieve(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7', nextToken: null)) >> {
      new DescribeSpotPriceHistoryResult(nextToken: 'more1', spotPriceHistory: [
        new SpotPrice(spotPrice: '1'),
        new SpotPrice(spotPrice: '2'),
        new SpotPrice(spotPrice: '3'),
      ])
    }

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7', nextToken: 'more1')) >> {
      new DescribeSpotPriceHistoryResult(nextToken: 'more2', spotPriceHistory: [
        new SpotPrice(spotPrice: '4'),
        new SpotPrice(spotPrice: '5'),
        new SpotPrice(spotPrice: '6'),
      ])
    }

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7', nextToken: 'more2')) >> {
      new DescribeSpotPriceHistoryResult(nextToken: null, spotPriceHistory: [
        new SpotPrice(spotPrice: '7'),
        new SpotPrice(spotPrice: '8'),
        new SpotPrice(spotPrice: '9'),
      ])
    }

    and:
    actual == [
      new SpotPrice(spotPrice: '1'),
      new SpotPrice(spotPrice: '2'),
      new SpotPrice(spotPrice: '3'),
      new SpotPrice(spotPrice: '4'),
      new SpotPrice(spotPrice: '5'),
      new SpotPrice(spotPrice: '6'),
      new SpotPrice(spotPrice: '7'),
      new SpotPrice(spotPrice: '8'),
      new SpotPrice(spotPrice: '9'),
    ]
    0 * _
  }

  def 'should retrieve only once if no tokens exist'() {
    when:
    List<SpotPrice> actual = retriever.retrieve(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7', nextToken: null)) >> {
      new DescribeSpotPriceHistoryResult(nextToken: null, spotPriceHistory: [
        new SpotPrice(spotPrice: '1'),
        new SpotPrice(spotPrice: '2'),
        new SpotPrice(spotPrice: '3'),
        new SpotPrice(spotPrice: '4'),
      ])
    }

    and:
    actual == [
      new SpotPrice(spotPrice: '1'),
      new SpotPrice(spotPrice: '2'),
      new SpotPrice(spotPrice: '3'),
      new SpotPrice(spotPrice: '4'),
    ]
    0 * _
  }

  def 'should retrieve up to limit'() {
    def retriever = new AwsResultsRetriever<SpotPrice, DescribeSpotPriceHistoryRequest,
      DescribeSpotPriceHistoryResult>(5) {
      DescribeSpotPriceHistoryResult makeRequest(DescribeSpotPriceHistoryRequest request) {
        service.describeSpotPriceHistory(request)
      }
      List<SpotPrice> accessResult(DescribeSpotPriceHistoryResult result) {
        result.spotPriceHistory
      }
      void limitRetrieval(DescribeSpotPriceHistoryRequest request, int remaining) {
        request.withMaxResults(Math.min(3, remaining))
      }
    }

    when:
    List<SpotPrice> actual = retriever.retrieve(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7',
      maxResults: 3, nextToken: null)) >> {
      new DescribeSpotPriceHistoryResult(nextToken: 'more1', spotPriceHistory: [
        new SpotPrice(spotPrice: '1'),
        new SpotPrice(spotPrice: '2'),
        new SpotPrice(spotPrice: '3'),
      ])
    }

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7',
      maxResults: 2, nextToken: 'more1')) >> {
      new DescribeSpotPriceHistoryResult(nextToken: 'more2', spotPriceHistory: [
        new SpotPrice(spotPrice: '4'),
        new SpotPrice(spotPrice: '5'),
      ])
    }

    and:
    actual == [
      new SpotPrice(spotPrice: '1'),
      new SpotPrice(spotPrice: '2'),
      new SpotPrice(spotPrice: '3'),
      new SpotPrice(spotPrice: '4'),
      new SpotPrice(spotPrice: '5'),
    ]
    0 * _
  }

  def 'should not enforce limit if limitRetrieval is not implemented'() {
    def retriever = new AwsResultsRetriever<SpotPrice, DescribeSpotPriceHistoryRequest,
      DescribeSpotPriceHistoryResult>(5) {
      DescribeSpotPriceHistoryResult makeRequest(DescribeSpotPriceHistoryRequest request) {
        service.describeSpotPriceHistory(request)
      }
      List<SpotPrice> accessResult(DescribeSpotPriceHistoryResult result) {
        result.spotPriceHistory
      }
    }

    when:
    List<SpotPrice> actual = retriever.retrieve(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7', nextToken: null)) >> {
      new DescribeSpotPriceHistoryResult(nextToken: 'more1', spotPriceHistory: [
        new SpotPrice(spotPrice: '1'),
        new SpotPrice(spotPrice: '2'),
        new SpotPrice(spotPrice: '3'),
      ])
    }

    then:
    1 * service.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest(availabilityZone: 'us-east-7', nextToken: 'more1')) >> {
      new DescribeSpotPriceHistoryResult(nextToken: 'more2', spotPriceHistory: [
        new SpotPrice(spotPrice: '4'),
        new SpotPrice(spotPrice: '5'),
        new SpotPrice(spotPrice: '6'),
      ])
    }

    and:
    actual == [
      new SpotPrice(spotPrice: '1'),
      new SpotPrice(spotPrice: '2'),
      new SpotPrice(spotPrice: '3'),
      new SpotPrice(spotPrice: '4'),
      new SpotPrice(spotPrice: '5'),
      new SpotPrice(spotPrice: '6'),
    ]
    0 * _
  }

}
