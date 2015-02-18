/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

describe('Service: awsServerGroupConfiguration', function () {

  beforeEach(
    module(
      'deckApp.aws.serverGroup.configure.service'
    )
  );


  beforeEach(inject(function (_awsServerGroupConfigurationService_) {
    this.service = _awsServerGroupConfigurationService_;
  }));

  describe('configureLoadBalancerOptions', function () {

    beforeEach(function() {
      this.allLoadBalancers = [
        {
          name: 'elb-1',
          accounts: [
            {
              name: 'test',
              regions: [
                {
                  name: 'us-east-1',
                  loadBalancers: [
                    { region: 'us-east-1', vpcId: null, name: 'elb-1' },
                    { region: 'us-east-1', vpcId: 'vpc-1', name: 'elb-1' },
                  ]
                }
              ]
            }
          ]
        },
        {
          name: 'elb-2',
          accounts: [
            {
              name: 'test',
              regions: [
                {
                  name: 'us-east-1',
                  loadBalancers: [
                    { region: 'us-east-1', vpcId: null, name: 'elb-2' },
                    { region: 'us-east-1', vpcId: 'vpc-2', name: 'elb-2' },
                  ]
                },
                {
                  name: 'us-west-1',
                  loadBalancers: [
                    { region: 'us-west-1', vpcId: null, name: 'elb-2' },
                  ]
                }
              ],
            }
          ]
        }
      ];

      this.command = {
        backingData: {
          loadBalancers: this.allLoadBalancers,
          filtered: {
            loadBalancers: ['elb-1', 'elb-2']
          }
        },
        loadBalancers: ['elb-1'],
        credentials: 'test',
        region: 'us-east-1',
        vpcId: null
      };
    });

    it('matches existing load balancers based on name - no VPC', function () {
      var result = this.service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: { }});
    });

    it('matches existing load balancers based on name - VPC', function() {
      this.command.vpcId = 'vpc-1';
      var result = this.service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: { }});
    });

    it('sets dirty all unmatched load balancers - no VPC', function () {
      this.command.region = 'us-west-1';
      this.command.loadBalancers = ['elb-1', 'elb-2'];
      var result = this.service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-2']);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-1']}});
    });

    it('sets dirty all unmatched load balancers - VPC', function () {
      this.command.loadBalancers = ['elb-1', 'elb-2'];
      this.command.vpcId = 'vpc-1';
      var result = this.service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-2']}});

      this.command.vpcId = 'vpc-2';
      result = this.service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual([]);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-1']}});
    });

    it('updates filteredData to new region - no VPC', function() {
      this.command.region = 'us-west-1';
      this.service.configureLoadBalancerOptions(this.command);
      expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-2']);
    });

    it('updates filteredData to new VPC', function() {
      this.command.vpcId = 'vpc-1';
      this.service.configureLoadBalancerOptions(this.command);
      expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-1']);
    });
  });

  describe('configureSecurityGroupOptions', function () {

    beforeEach(function() {
      this.allSecurityGroups = {
        test: {
          aws: {
            'us-west-1': [
              { name: 'sg1', id: 'sg-1a', vpcId: null},
              { name: 'sg2', id: 'sg-2a', vpcId: null},
              { name: 'sg3', id: 'sg-3a', vpcId: null},
              { name: 'sg1', id: 'sg-1va', vpcId: 'vpc-1'},
              { name: 'sg2', id: 'sg-2va', vpcId: 'vpc-1'},
              { name: 'sg3', id: 'sg-3va', vpcId: 'vpc-2'}
            ],
            'us-east-1': [
              { name: 'sg1', id: 'sg-1c', vpcId: null},
              { name: 'sg2', id: 'sg-2c', vpcId: null},
              { name: 'sg1', id: 'sg-1vc', vpcId: 'vpc-3'},
              { name: 'sg2', id: 'sg-2vc', vpcId: 'vpc-4'}
            ]
          }
        }
      };

      this.command = {
        backingData: {
          securityGroups: this.allSecurityGroups,
          filtered: {
            securityGroups: this.allSecurityGroups.test.aws['us-west-1']
          }
        },
        securityGroups: ['sg-1a', 'sg-2a'],
        credentials: 'test',
        region: 'us-west-1'
      };
    });

    it('matches existing security groups based on name - no VPC', function () {
      this.command.region = 'us-east-1';

      var result = this.service.configureSecurityGroupOptions(this.command);

      expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
      expect(result).toEqual({ dirty: {} });
    });

    it('matches existing security groups based on name - VPC', function() {
      this.command.vpcId = 'vpc-1';

      var result = this.service.configureSecurityGroupOptions(this.command);

      expect(this.command.securityGroups).toEqual(['sg-1va', 'sg-2va']);
      expect(result).toEqual({ dirty: { }});
    });

    it('matches on name or id, converting to id when name encountered', function() {
      this.command.securityGroups = ['sg1', 'sg-2a'];
      this.command.region = 'us-east-1';

      var result = this.service.configureSecurityGroupOptions(this.command);

      expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
      expect(result).toEqual({ dirty: {}});
    });

    it('sets dirty all unmatched security groups - no VPC', function () {
      this.command.securityGroups.push('sg-3a');
      this.command.region = 'us-east-1';

      var result = this.service.configureSecurityGroupOptions(this.command);

      expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
      expect(result).toEqual({ dirty: { securityGroups: ['sg3'] }});
    });

    it('sets dirty all unmatched security groups - VPC', function () {
      this.command.securityGroups.push('sg-3a');
      this.command.vpcId = 'vpc-2';

      var result = this.service.configureSecurityGroupOptions(this.command);

      expect(this.command.securityGroups).toEqual(['sg-3va']);
      expect(result).toEqual({ dirty: { securityGroups: ['sg1', 'sg2'] }});
    });

    it('updates filteredData to new region - no VPC', function() {
      var expected = this.allSecurityGroups.test.aws['us-east-1'].slice(0, 2);
      this.command.region = 'us-east-1';
      this.service.configureSecurityGroupOptions(this.command);
      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
    });

    it('updates filteredData to new VPC', function() {
      var expected = this.allSecurityGroups.test.aws['us-west-1'].slice(3, 5);
      this.command.vpcId = 'vpc-1';
      this.service.configureSecurityGroupOptions(this.command);
      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
    });

  });

  describe('configureKeyPairs', function() {
    beforeEach(function() {
      this.command = {
        backingData: {
          filtered: {},
          regionsKeyedByAccount: {
            test: {
              defaultKeyPair: 'test-pair'
            },
            prod: {
              defaultKeyPair: 'prod-pair'
            },
            nothing: {}
          },
          keyPairs: [
            { account: 'test', region: 'us-west-1', keyName: 'test-pair' },
            { account: 'test', region: 'us-east-1', keyName: 'test-pair' },
            { account: 'test', region: 'us-west-1', keyName: 'shared' },
            { account: 'prod', region: 'us-west-1', keyName: 'prod-pair' },
            { account: 'prod', region: 'us-west-1', keyName: 'shared' },
          ]
        },
        credentials: 'test',
        region: 'us-west-1',
        keyPair: 'shared',
      };
    });

    it('retains keyPair when found in new account', function() {
      this.command.credentials = 'prod';
      this.service.configureKeyPairs(this.command);
      expect(this.command.keyPair).toBe('shared');
    });

    it('retains keyPair when found in new region', function() {
      this.command.region = 'us-east-1';
      this.command.keyPair = 'test-pair';
      this.service.configureKeyPairs(this.command);
      expect(this.command.keyPair).toBe('test-pair');
    });

    it('marks dirty, sets to new default value when key pair not found in new account', function() {
      this.command.credentials = 'prod';
      this.command.keyPair = 'test-pair';
      var result = this.service.configureKeyPairs(this.command);
      expect(result.dirty.keyPair).toBe(true);
      expect(this.command.keyPair).toBe('prod-pair');
    });

    it('marks dirty, sets to default value when key pair not found in new region', function() {
      this.command.region = 'us-east-1';
      var result = this.service.configureKeyPairs(this.command);
      expect(result.dirty.keyPair).toBe(true);
      expect(this.command.keyPair).toBe('test-pair');
    });

  });

});
