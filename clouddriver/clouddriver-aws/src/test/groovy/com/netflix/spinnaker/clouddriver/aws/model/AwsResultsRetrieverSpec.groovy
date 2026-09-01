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
package com.netflix.spinnaker.clouddriver.aws.model

import software.amazon.awssdk.services.ec2.model.DescribeSpotPriceHistoryRequest
import software.amazon.awssdk.services.ec2.model.DescribeSpotPriceHistoryResponse
import software.amazon.awssdk.services.ec2.model.SpotPrice
import com.netflix.spinnaker.clouddriver.aws.model.AwsResultsRetriever
import spock.lang.Specification

class AwsResultsRetrieverSpec  extends Specification {

  def service = Mock(AwsEc2Service)

  def retriever = new AwsResultsRetriever<SpotPrice, DescribeSpotPriceHistoryRequest, DescribeSpotPriceHistoryResponse>() {
    DescribeSpotPriceHistoryResponse makeRequest(DescribeSpotPriceHistoryRequest request) {
      service.describeSpotPriceHistory(request)
    }
    List<SpotPrice> accessResult(DescribeSpotPriceHistoryResponse result) {
      result.spotPriceHistory()
    }
    DescribeSpotPriceHistoryRequest setNextToken(DescribeSpotPriceHistoryRequest request, String nextToken) {
      request.toBuilder().nextToken(nextToken).build()
    }
  }

  static interface AwsEc2Service {
    DescribeSpotPriceHistoryResponse describeSpotPriceHistory(DescribeSpotPriceHistoryRequest request)
  }

  static DescribeSpotPriceHistoryRequest request(String availabilityZone, String nextToken = null, Integer maxResults = null) {
    def builder = DescribeSpotPriceHistoryRequest.builder().availabilityZone(availabilityZone).nextToken(nextToken)
    if (maxResults != null) {
      builder.maxResults(maxResults)
    }
    builder.build()
  }

  static SpotPrice spotPrice(String price) {
    SpotPrice.builder().spotPrice(price).build()
  }

  void "should retrieve for all tokens"() {
    when:
    List<SpotPrice> actual = retriever.retrieve(request('us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', null)) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken('more1').spotPriceHistory([
        spotPrice('1'),
        spotPrice('2'),
        spotPrice('3'),
      ]).build()
    }

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', 'more1')) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken('more2').spotPriceHistory([
        spotPrice('4'),
        spotPrice('5'),
        spotPrice('6'),
      ]).build()
    }

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', 'more2')) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken(null).spotPriceHistory([
        spotPrice('7'),
        spotPrice('8'),
        spotPrice('9'),
      ]).build()
    }

    and:
    actual == [
      spotPrice('1'),
      spotPrice('2'),
      spotPrice('3'),
      spotPrice('4'),
      spotPrice('5'),
      spotPrice('6'),
      spotPrice('7'),
      spotPrice('8'),
      spotPrice('9'),
    ]
    0 * _
  }

  void "should retrieve only once if no tokens exist"() {
    when:
    List<SpotPrice> actual = retriever.retrieve(request('us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', null)) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken(null).spotPriceHistory([
        spotPrice('1'),
        spotPrice('2'),
        spotPrice('3'),
        spotPrice('4'),
      ]).build()
    }

    and:
    actual == [
      spotPrice('1'),
      spotPrice('2'),
      spotPrice('3'),
      spotPrice('4'),
    ]
    0 * _
  }

  void "should retrieve up to limit"() {
    def retriever = new AwsResultsRetriever<SpotPrice, DescribeSpotPriceHistoryRequest,
      DescribeSpotPriceHistoryResponse>(5) {
      DescribeSpotPriceHistoryResponse makeRequest(DescribeSpotPriceHistoryRequest request) {
        service.describeSpotPriceHistory(request)
      }
      List<SpotPrice> accessResult(DescribeSpotPriceHistoryResponse result) {
        result.spotPriceHistory()
      }
      DescribeSpotPriceHistoryRequest setNextToken(DescribeSpotPriceHistoryRequest request, String nextToken) {
        request.toBuilder().nextToken(nextToken).build()
      }
      DescribeSpotPriceHistoryRequest limitRetrieval(DescribeSpotPriceHistoryRequest request, int remaining) {
        request.toBuilder().maxResults(Math.min(3, remaining)).build()
      }
    }

    when:
    List<SpotPrice> actual = retriever.retrieve(request('us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', null, 3)) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken('more1').spotPriceHistory([
        spotPrice('1'),
        spotPrice('2'),
        spotPrice('3'),
      ]).build()
    }

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', 'more1', 2)) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken('more2').spotPriceHistory([
        spotPrice('4'),
        spotPrice('5'),
      ]).build()
    }

    and:
    actual == [
      spotPrice('1'),
      spotPrice('2'),
      spotPrice('3'),
      spotPrice('4'),
      spotPrice('5'),
    ]
    0 * _
  }

  void "should not enforce limit if limitRetrieval is not implemented"() {
    def retriever = new AwsResultsRetriever<SpotPrice, DescribeSpotPriceHistoryRequest,
      DescribeSpotPriceHistoryResponse>(5) {
      DescribeSpotPriceHistoryResponse makeRequest(DescribeSpotPriceHistoryRequest request) {
        service.describeSpotPriceHistory(request)
      }
      List<SpotPrice> accessResult(DescribeSpotPriceHistoryResponse result) {
        result.spotPriceHistory()
      }
      DescribeSpotPriceHistoryRequest setNextToken(DescribeSpotPriceHistoryRequest request, String nextToken) {
        request.toBuilder().nextToken(nextToken).build()
      }
    }

    when:
    List<SpotPrice> actual = retriever.retrieve(request('us-east-7'))

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', null)) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken('more1').spotPriceHistory([
        spotPrice('1'),
        spotPrice('2'),
        spotPrice('3'),
      ]).build()
    }

    then:
    1 * service.describeSpotPriceHistory(request('us-east-7', 'more1')) >> {
      DescribeSpotPriceHistoryResponse.builder().nextToken('more2').spotPriceHistory([
        spotPrice('4'),
        spotPrice('5'),
        spotPrice('6'),
      ]).build()
    }

    and:
    actual == [
      spotPrice('1'),
      spotPrice('2'),
      spotPrice('3'),
      spotPrice('4'),
      spotPrice('5'),
      spotPrice('6'),
    ]
    0 * _
  }

}
