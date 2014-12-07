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

import spock.lang.Specification

class GoogleLoadBalancerControllerSpec extends Specification {
  void "returned summary list is built for zero accounts"() {
    setup:
      def networkLoadBalancerMap = [:]

    when:
      def summaryList = GoogleLoadBalancerController.getSummaryForLoadBalancers(networkLoadBalancerMap)

    then:
      summaryList.size == 0

    when:
      summaryList = GoogleLoadBalancerController.getSummaryForLoadBalancers(null)

    then:
      summaryList.size == 0
  }

  void "returned summary list is built for one account"() {
    setup:
      def networkLoadBalancerMap = [
        'my-account-name': [
          'us-central1' : ['lb1', 'lb2', 'lb3'],
          'europe-west1': ['lb4', 'lb5', 'lb6'],
          'asia-east1'  : ['lb7', 'lb8', 'lb9']
        ]
      ]

    when:
      def summaryList = GoogleLoadBalancerController.getSummaryForLoadBalancers(networkLoadBalancerMap)

    then:
      summaryList.size == 1
      summaryList[0].account == 'my-account-name'
      summaryList[0].regions.collect { it.name } as Set == ['us-central1', 'europe-west1', 'asia-east1'] as Set
      summaryList[0].regions.find {
        it.name == 'us-central1'
      }.loadBalancers.collect { it.name } == ['lb1', 'lb2', 'lb3']
      summaryList[0].regions.find {
        it.name == 'europe-west1'
      }.loadBalancers.collect { it.name } == ['lb4', 'lb5', 'lb6']
      summaryList[0].regions.find {
        it.name == 'asia-east1'
      }.loadBalancers.collect { it.name } == ['lb7', 'lb8', 'lb9']
  }

  void "returned summary list is built for multiple accounts"() {
    setup:
      def networkLoadBalancerMap = [
        'my-account-name-1': [
          'us-central1' : ['lb1', 'lb2', 'lb3'],
          'europe-west1': ['lb4', 'lb5', 'lb6']
        ],
        'my-account-name-2': [
          'asia-east1'  : ['lb7', 'lb8', 'lb9'],
          'europe-west1': ['lb10', 'lb11', 'lb12']
        ]
      ]

    when:
      def summaryList = GoogleLoadBalancerController.getSummaryForLoadBalancers(networkLoadBalancerMap)

    then:
      summaryList.size == 2
      summaryList[0].account == 'my-account-name-1'
      summaryList[0].regions.collect { it.name } as Set == ['us-central1', 'europe-west1'] as Set
      summaryList[0].regions.find {
        it.name == 'us-central1'
      }.loadBalancers.collect { it.name } == ['lb1', 'lb2', 'lb3']
      summaryList[0].regions.find {
        it.name == 'europe-west1'
      }.loadBalancers.collect { it.name } == ['lb4', 'lb5', 'lb6']

      summaryList[1].account == 'my-account-name-2'
      summaryList[1].regions.collect { it.name } as Set == ['asia-east1', 'europe-west1'] as Set
      summaryList[1].regions.find {
        it.name == 'asia-east1'
      }.loadBalancers.collect { it.name } == ['lb7', 'lb8', 'lb9']
      summaryList[1].regions.find {
        it.name == 'europe-west1'
      }.loadBalancers.collect { it.name } == ['lb10', 'lb11', 'lb12']
  }
}
