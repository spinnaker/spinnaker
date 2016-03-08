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

describe('Service: azureServerGroupConfiguration', function () {

  var service, $q, accountService, securityGroupReader,
    cacheInitializer, loadBalancerReader, $scope;

  beforeEach(
    window.module(
      require('./serverGroupConfiguration.service.js')
      )
    );


  beforeEach(window.inject(function (_azureServerGroupConfigurationService_, _$q_, _accountService_,
    _securityGroupReader_, _cacheInitializer_,
    _loadBalancerReader_, $rootScope) {
    service = _azureServerGroupConfigurationService_;
    $q = _$q_;
    accountService = _accountService_;
    securityGroupReader = _securityGroupReader_;
    cacheInitializer = _cacheInitializer_;
    loadBalancerReader = _loadBalancerReader_;
    $scope = $rootScope.$new();

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
  }));

  xdescribe('configureCommand', function () {
    it('attempts to reload load balancers if some are not found on initialization, but does not set dirty flag', function () {
      spyOn(accountService, 'getCredentialsKeyedByAccount').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when([]));
      spyOn(loadBalancerReader, 'listLoadBalancers').and.returnValue($q.when(this.allLoadBalancers));
      spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when([]));
      spyOn(cacheInitializer, 'refreshCache').and.returnValue($q.when(null));

      var command = {
        credentials: 'test',
        region: 'us-east-1',
        loadBalancers: ['elb-1', 'elb-3'],
        vpcId: null,
        viewState: {
          disableImageSelection: true,
        }
      };

      service.configureCommand({}, command);
      $scope.$digest();
      $scope.$digest();

      expect(cacheInitializer.refreshCache).toHaveBeenCalledWith('loadBalancers');
      expect(cacheInitializer.refreshCache.calls.count()).toBe(1);
      expect(loadBalancerReader.listLoadBalancers.calls.count()).toBe(2);
      expect(command.dirty).toBeUndefined();

    });

    it('attempts to reload security groups if some are not found on initialization, but does not set dirty flag', function () {
      spyOn(accountService, 'getCredentialsKeyedByAccount').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when([]));
      spyOn(loadBalancerReader, 'listLoadBalancers').and.returnValue($q.when(this.allLoadBalancers));
      spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when([]));
      spyOn(cacheInitializer, 'refreshCache').and.returnValue($q.when(null));

      var command = {
        credentials: 'test',
        region: 'us-east-1',
        securityGroups: ['sg-1'],
        vpcId: null,
        viewState: {
          disableImageSelection: true,
        }
      };

      service.configureCommand({}, command);
      $scope.$digest();
      $scope.$digest();

      expect(cacheInitializer.refreshCache).toHaveBeenCalledWith('securityGroups');
      expect(cacheInitializer.refreshCache.calls.count()).toBe(1);
      expect(securityGroupReader.getAllSecurityGroups.calls.count()).toBe(2);
      expect(command.dirty).toBeUndefined();

    });

    it('attempts to reload instance types if previous selection is not found on initialization, but does not set dirty flag', function () {
      spyOn(accountService, 'getCredentialsKeyedByAccount').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when([]));
      spyOn(loadBalancerReader, 'listLoadBalancers').and.returnValue($q.when([]));
      spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when([]));
      spyOn(cacheInitializer, 'refreshCache').and.returnValue($q.when(null));

      var command = {
        credentials: 'test',
        region: 'us-east-1',
        securityGroups: [],
        instanceType: 'm4.tiny',
        vpcId: null,
        viewState: {
          disableImageSelection: true,
        }
      };

      service.configureCommand({}, command);
      $scope.$digest();
      $scope.$digest();

      expect(cacheInitializer.refreshCache).toHaveBeenCalledWith('instanceTypes');
      expect(cacheInitializer.refreshCache.calls.count()).toBe(1);
      expect(command.dirty).toBeUndefined();
    });

  });

  xdescribe('configureLoadBalancerOptions', function () {

    beforeEach(function () {

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
      var result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: {} });
    });

    it('matches existing load balancers based on name - VPC', function () {
      this.command.vpcId = 'vpc-1';
      var result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: {} });
    });

    it('sets dirty all unmatched load balancers - no VPC', function () {
      this.command.region = 'us-west-1';
      this.command.loadBalancers = ['elb-1', 'elb-2'];
      var result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-2']);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-1'] } });
    });

    it('sets dirty all unmatched load balancers - VPC', function () {
      this.command.loadBalancers = ['elb-1', 'elb-2'];
      this.command.vpcId = 'vpc-1';
      var result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual(['elb-1']);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-2'] } });

      this.command.vpcId = 'vpc-2';
      result = service.configureLoadBalancerOptions(this.command);

      expect(this.command.loadBalancers).toEqual([]);
      expect(result).toEqual({ dirty: { loadBalancers: ['elb-1'] } });
    });

    it('updates filteredData to new region - no VPC', function () {
      this.command.region = 'us-west-1';
      service.configureLoadBalancerOptions(this.command);
      expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-2']);
    });

    it('updates filteredData to new VPC', function () {
      this.command.vpcId = 'vpc-1';
      service.configureLoadBalancerOptions(this.command);
      expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-1']);
    });
  });

  describe('configureSecurityGroupOptions', function () {

    beforeEach(function () {
      this.allSecurityGroups = {
        azurecred1: {
          eastus: [
            {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'eastus'
              }
            },{
              'onlyazure-cache': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-cache',
                name: 'onlyazure-cache',
                network: 'na',
                provider: 'azure',
                region: 'eastus'
              }
            }
          ],
          westus: [
            {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'westus'
              }
            }
          ]
        }
      };

      this.command = {
        backingData: {
          securityGroups: this.allSecurityGroups,
          filtered: {}
        },
        viewState: {
          securityGroupsConfigured: false
        },
        credentials: 'azurecred1',
        region: 'westus'
      };
    });

    it('finds matching security groups and assigns them to the filtered list the first time', function () {
      this.command.region = 'westus';
      var expected = this.allSecurityGroups.azurecred1['westus'];

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.viewState.securityGroupConfigured).toBeTrue;
    });

    it('finds matcing security groups, sets dirty flag for subsequent time', function () {
      this.command.region = 'eastus';
      this.command.backingData.filtered.securityGroups = this.allSecurityGroups.azurecred1['westus'];
      var expected = this.allSecurityGroups.azurecred1['eastus'];

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.viewState.securityGroupConfigured).toBeTrue;
    });

    it('clears the selected securityGroup', function () {
      this.command.selectedSecurityGroup = {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'westus'
              }
            };
      this.command.region = 'eastus';

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.selectedSecurityGroup).toBeNull;
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.viewState.securityGroupConfigured).toBeTrue;
    });

    it('returns no security groups if none match', function() {
      this.command.region = 'eastasia';
      this.command.backingData.filtered.securityGroups = this.allSecurityGroups.azurecred1['westus'];

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.selectedSecurityGroup).toBeUndefined;
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.backingData.filtered.securityGroups).toEqual([]);
      expect(this.command.viewState.securityGroupConfigured).toBeFalse;
    });
  });
});
