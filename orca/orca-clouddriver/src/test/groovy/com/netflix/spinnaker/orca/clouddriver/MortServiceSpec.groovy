/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver

import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Unroll
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper

import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.SecurityGroupIngress
import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.applyMappings
import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.filterForSecurityGroupIngress
import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.findById
import static com.netflix.spinnaker.orca.clouddriver.MortService.VPC.findForRegionAndAccount

class MortServiceSpec extends Specification {
  void "should extract only the security group ingress rules from SecurityGroup"() {
    given:
    def mortService = Mock(MortService) {
      1 * getSearchResults("sg-3", "securityGroups") >> Calls.response(
        [
          new MortService.SearchResult(
            results: [
              [name: "SG3"]
            ]
          )
        ])
    }
    def currentSecurityGroup = new MortService.SecurityGroup(
      inboundRules: [
        [
          securityGroup: [name: "SG1"],
          protocol     : "tcp",
          portRanges   : [[startPort: 7000, endPort: 7001], [startPort: 7001, endPort: 7002]]
        ],
        [
          securityGroup: [id: "sg-3"],
          protocol     : "tcp",
          portRanges   : [[startPort: 7002, endPort: 7003]]
        ],
        [
          range     : [ip: "127.0.0.1", cidr: "/32"],
          protocol  : "tcp",
          portRanges: [[startPort: 7000, endPort: 7001]]
        ]
      ]
    )

    when:
    def securityGroupIngress = filterForSecurityGroupIngress(mortService, currentSecurityGroup)

    then:
    securityGroupIngress == [
      new SecurityGroupIngress("SG1", 7000, 7001, "tcp"),
      new SecurityGroupIngress("SG1", 7001, 7002, "tcp"),
      new SecurityGroupIngress("SG3", 7002, 7003, "tcp")
    ] as List<Map>
  }

  @Unroll
  void "should apply security group mappings"() {
    given:
    def securityGroupMappings = [
      "nf-datacenter-vpc": "nf-datacenter"
    ]

    expect:
    applyMappings(securityGroupMappings, sourceNames.collect {
      new SecurityGroupIngress(it)
    })*.name as List<String> == expectedNames

    where:
    sourceNames                     || expectedNames
    ["nf-datacenter-vpc", "foobar"] || ["nf-datacenter", "foobar"]
  }

  void "should find VPC for region and account"() {
    given:
    def allVPCs = [
      new MortService.VPC(id: "vpc1-0", name: "vpc1", region: "us-west-1", account: "test"),
      new MortService.VPC(id: "vpc1-1", name: "vpc1", region: "us-east-1", account: "test"),
    ]

    expect:
    findForRegionAndAccount(allVPCs, "vpc1-0", "us-west-1", "test") == allVPCs[0]
    findForRegionAndAccount(allVPCs, "vpc1-1", "us-west-1", "test") == allVPCs[0]
    findForRegionAndAccount(allVPCs, "vpc1-0", "us-east-1", "test") == allVPCs[1]
  }

  void "should find VPC for region and account given name"() {
    given:
    def allVPCs = [
      new MortService.VPC(id: "vpc1-2", name: null, region: "us-east-1", account: "test"),
      new MortService.VPC(id: "vpc1-0", name: "vpc1", region: "us-west-1", account: "test"),
      new MortService.VPC(id: "vpc1-1", name: "vpc1", region: "us-east-1", account: "test"),
    ]

    expect:
    findForRegionAndAccount(allVPCs, "vpc1", "us-west-1", "test") == allVPCs[1]
    findForRegionAndAccount(allVPCs, "vpc1", "us-east-1", "test") == allVPCs[2]
    findForRegionAndAccount(allVPCs, "VPC1", "us-east-1", "test") == allVPCs[2]
  }

  @Unroll
  void "should throw exception if VPC cannot be found"() {
    given:
    def allVPCs = [
      new MortService.VPC(id: "vpc1-0", name: "vpc1", region: "us-west-1", account: "test"),
      new MortService.VPC(id: "vpc1-1", name: "vpc1", region: "us-east-1", account: "test"),
    ]

    when:
    findForRegionAndAccount(allVPCs, sourceVpcId, region, account)

    then:
    thrown(IllegalStateException)

    where:
    sourceVpcId | region      | account
    "vpc1-0"    | "us-west-2" | "test"
    "vpc1-X"    | "us-west-1" | "test"
    "vpc1-0"    | "us-west-1" | "prod"
  }

  void "should find security group by id"() {
    given:
    def mortService = Mock(MortService) {
      1 * getSearchResults(sg.id, "securityGroups") >> Calls.response(
        [
          new MortService.SearchResult(
            platform: "aws",
            results: [
              [account: sg.accountName, name: sg.name, region: sg.region, vpcId: sg.vpcId]
            ]
          )
        ])
      1 * getSecurityGroup(sg.accountName, sg.type, sg.name, sg.region, sg.vpcId ) >> Calls.response(sg)
      1 * getSearchResults("does-not-exist", "securityGroups") >> Calls.response([])
      0 * _
    }

    expect:
    findById(mortService, sg.id) == sg

    try {
      findById(mortService, "does-not-exist")
      assert false
    } catch (IllegalArgumentException e) {
      // expected
    }

    where:
    sg = new MortService.SecurityGroup(
      type: "aws",
      id: "sg-1",
      name: "SG1",
      description: "Description",
      accountName: "test",
      region: "us-west-1",
      vpcId: "vpc-12345"
    )

  }

  def "should correctly map VPC properties from JSON response"() {
    given:
    def objectMapper = OrcaObjectMapper.newInstance()
    def vpcResponse = """
      {
        "account": "test",
        "id": "vpc-12345",
        "name": "vpc1",
        "region": "us-west-1"
      }
    """

    when:
    def vpc = objectMapper.readValue(vpcResponse, MortService.VPC.class)

    then:
    vpc.account == "test"
    vpc.id == "vpc-12345"
    vpc.region == "us-west-1"
    vpc.name == "vpc1"
  }
}
