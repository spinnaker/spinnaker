/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.data

import spock.lang.Specification

class InstanceFamilyUtilsSpec extends Specification {

  def 'support for bursting is reported correctly for instance type'() {
    when:
    def result = InstanceFamilyUtils.isBurstingSupported(instanceType)

    then:
    result == expectedResult

    where:
    instanceType    | expectedResult
    't2.large'      | true
    't3.small'      | true
    't3a.micro'     | true
    'c3.small'      | false
  }

  def 'compatible ami virtualization #virtualization and known family does not throw exception'() {
    when:
    InstanceFamilyUtils.isAmiAndFamilyCompatible(virtualization, instanceType)

    then:
    notThrown(IllegalArgumentException)

    where:
    virtualization   | instanceType
    'hvm'            | 't2.large'
    'hvm'            | 't3.small'
    'paravirtual'    | 'c3.small'
    'paravirtual'    | 't1.small'
  }

  def 'compatibility is assumed to be true if instance family is unknown'() {
    when:
    InstanceFamilyUtils.isAmiAndFamilyCompatible(virtualization, instanceType)

    then:
    notThrown(IllegalArgumentException)

    where:
    virtualization | instanceType
    'paravirtual'  | 'c5.large'
    'paravirtual'  | 't3a.small'
  }

  def 'incompatible ami virtualization #virtualization and known family throws exception'() {
    when:
    InstanceFamilyUtils.isAmiAndFamilyCompatible(virtualization, instanceType)

    then:
    thrown(IllegalArgumentException)

    where:
    virtualization | instanceType
    'paravirtual'  | 't2.large'
    'paravirtual'  | 't3.small'
    'hvm'          | 'm1.small'
    'hvm'          | 't1.small'
  }

  def 'default ebs optimized is reported correctly for instance type'() {
    expect:
    InstanceFamilyUtils.getDefaultEbsOptimizedFlag(instanceType) == expectedResult

    where:
    instanceType    | expectedResult
    't2.large'      | false
    'c3.small'      | false
    'c4.small'      | true
    'm4.large'      | true
    'm5.large'      | false
    'invalid'       | false
  }
}