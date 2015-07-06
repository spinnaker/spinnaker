/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.controllers

import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import spock.lang.Specification

class GoogleLoadBalancerControllerSpec extends Specification {
  void "returned summary map is built for zero accounts"() {
    setup:
      def networkLoadBalancerMap = [:]

    when:
      def summaryMap = GoogleLoadBalancerController.getSummaryForLoadBalancers(networkLoadBalancerMap)

    then:
      summaryMap.size() == 0

    when:
      summaryMap = GoogleLoadBalancerController.getSummaryForLoadBalancers(null)

    then:
      summaryMap.size() == 0
  }

  void "returned summary map is built for one account"() {
    setup:
      def networkLoadBalancerMap = [
        'my-account-name': [
          'us-central1' : [new GoogleLoadBalancer(name: 'lb1', account: 'some-account', region: 'us-central1'),
                           new GoogleLoadBalancer(name: 'lb2', account: 'some-account', region: 'us-central1'),
                           new GoogleLoadBalancer(name: 'lb3', account: 'some-account', region: 'us-central1')],
          'europe-west1': [new GoogleLoadBalancer(name: 'lb4', account: 'some-account', region: 'europe-west1'),
                           new GoogleLoadBalancer(name: 'lb5', account: 'some-account', region: 'europe-west1'),
                           new GoogleLoadBalancer(name: 'lb6', account: 'some-account', region: 'europe-west1')],
          'asia-east1'  : [new GoogleLoadBalancer(name: 'lb7', account: 'some-account', region: 'asia-east1'),
                           new GoogleLoadBalancer(name: 'lb8', account: 'some-account', region: 'asia-east1'),
                           new GoogleLoadBalancer(name: 'lb9', account: 'some-account', region: 'asia-east1')],
        ]
      ]

    when:
      def summaryMap = GoogleLoadBalancerController.getSummaryForLoadBalancers(networkLoadBalancerMap)

    then:
      summaryMap.keySet() == ['lb1', 'lb2', 'lb3', 'lb4', 'lb5', 'lb6', 'lb7', 'lb8', 'lb9'] as Set
      def regions = [] as Set
      summaryMap.values().each { summary ->
        summary.accounts.collect { it.name } == ['my-account-name']
        regions << summary.accounts*.regions.name
      }
      regions.flatten() == ['us-central1', 'europe-west1', 'asia-east1'] as Set
  }

  void "returned summary map is built for multiple accounts"() {
    setup:
      def networkLoadBalancerMap = [
        'my-account-name': [
          'us-central1' : [new GoogleLoadBalancer(name: 'lb1', account: 'some-account', region: 'us-central1'),
                           new GoogleLoadBalancer(name: 'lb2', account: 'some-account', region: 'us-central1'),
                           new GoogleLoadBalancer(name: 'lb3', account: 'some-account', region: 'us-central1')],
          'europe-west1': [new GoogleLoadBalancer(name: 'lb4', account: 'some-account', region: 'europe-west1'),
                           new GoogleLoadBalancer(name: 'lb5', account: 'some-account', region: 'europe-west1'),
                           new GoogleLoadBalancer(name: 'lb6', account: 'some-account', region: 'europe-west1')],
          'asia-east1'  : [new GoogleLoadBalancer(name: 'lb7', account: 'some-account', region: 'asia-east1'),
                           new GoogleLoadBalancer(name: 'lb8', account: 'some-account', region: 'asia-east1'),
                           new GoogleLoadBalancer(name: 'lb9', account: 'some-account', region: 'asia-east1')],
        ],
        'my-account-name-2': [
          'asia-east1'  : [new GoogleLoadBalancer(name: 'lb10', account: 'some-account', region: 'asia-east1'),
                           new GoogleLoadBalancer(name: 'lb11', account: 'some-account', region: 'asia-east1'),
                           new GoogleLoadBalancer(name: 'lb12', account: 'some-account', region: 'asia-east1')],
          'europe-west1': [new GoogleLoadBalancer(name: 'lb13', account: 'some-account', region: 'europe-west1'),
                           new GoogleLoadBalancer(name: 'lb14', account: 'some-account', region: 'europe-west1'),
                           new GoogleLoadBalancer(name: 'lb15', account: 'some-account', region: 'europe-west1')]
        ]
      ]

    when:
      def summaryMap = GoogleLoadBalancerController.getSummaryForLoadBalancers(networkLoadBalancerMap)

    then:
      summaryMap.keySet() == ['lb1', 'lb2', 'lb3', 'lb4', 'lb5', 'lb6', 'lb7', 'lb8', 'lb9', 'lb10', 'lb11', 'lb12',
                              'lb13', 'lb14', 'lb15'] as Set
      def regions = [] as Set
      summaryMap.values().each { summary ->
        summary.accounts.collect { it.name } == ['my-account-name', 'my-account-name-2'] as Set
        regions << summary.accounts*.regions.name
      }
      regions.flatten() == ['us-central1', 'europe-west1', 'asia-east1'] as Set
  }
}
