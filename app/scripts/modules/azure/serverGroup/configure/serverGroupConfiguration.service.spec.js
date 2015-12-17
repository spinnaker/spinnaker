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

 xdescribe('Service: azureServerGroupConfiguration', function () {

   var service, $q, azureImageReader, accountService, securityGroupReader,
     azureInstanceTypeService, cacheInitializer,
     subnetReader, keyPairsReader, loadBalancerReader, $scope;

   beforeEach(
     window.module(
       require('./serverGroupConfiguration.service.js')
     )
   );


   beforeEach(window.inject(function (_azureServerGroupConfigurationService_, _$q_, _azureImageReader_, _accountService_,
                                      _securityGroupReader_, _azureInstanceTypeService_, _cacheInitializer_,
                                      _subnetReader_, _keyPairsReader_, _loadBalancerReader_, $rootScope) {
     service = _azureServerGroupConfigurationService_;
     $q = _$q_;
     azureImageReader = _azureImageReader_;
     accountService = _accountService_;
     securityGroupReader = _securityGroupReader_;
     azureInstanceTypeService = _azureInstanceTypeService_;
     cacheInitializer = _cacheInitializer_;
     subnetReader = _subnetReader_;
     keyPairsReader = _keyPairsReader_;
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

   describe('configureCommand', function () {
     it ('attempts to reload load balancers if some are not found on initialization, but does not set dirty flag', function () {
       spyOn(accountService, 'getRegionsKeyedByAccount').and.returnValue($q.when([]));
       spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when([]));
       spyOn(loadBalancerReader, 'listLoadBalancers').and.returnValue($q.when(this.allLoadBalancers));
       spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
       spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when([]));
       spyOn(keyPairsReader, 'listKeyPairs').and.returnValue($q.when([]));
       spyOn(azureInstanceTypeService, 'getAllTypesByRegion').and.returnValue($q.when([]));
       spyOn(cacheInitializer, 'refreshCache').and.returnValue($q.when(null));

       var command = {
         credentials: 'test',
         region: 'us-east-1',
         loadBalancers: [ 'elb-1', 'elb-3' ],
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

     it ('attempts to reload security groups if some are not found on initialization, but does not set dirty flag', function () {
       spyOn(accountService, 'getRegionsKeyedByAccount').and.returnValue($q.when([]));
       spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when([]));
       spyOn(loadBalancerReader, 'listLoadBalancers').and.returnValue($q.when(this.allLoadBalancers));
       spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
       spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when([]));
       spyOn(keyPairsReader, 'listKeyPairs').and.returnValue($q.when([]));
       spyOn(azureInstanceTypeService, 'getAllTypesByRegion').and.returnValue($q.when([]));
       spyOn(cacheInitializer, 'refreshCache').and.returnValue($q.when(null));

       var command = {
         credentials: 'test',
         region: 'us-east-1',
         securityGroups: [ 'sg-1' ],
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

     it ('attempts to reload instance types if previous selection is not found on initialization, but does not set dirty flag', function () {
       spyOn(accountService, 'getRegionsKeyedByAccount').and.returnValue($q.when([]));
       spyOn(securityGroupReader, 'getAllSecurityGroups').and.returnValue($q.when([]));
       spyOn(loadBalancerReader, 'listLoadBalancers').and.returnValue($q.when([]));
       spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
       spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when([]));
       spyOn(keyPairsReader, 'listKeyPairs').and.returnValue($q.when([]));
       spyOn(azureInstanceTypeService, 'getAllTypesByRegion').and.returnValue($q.when([]));
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
       expect(azureInstanceTypeService.getAllTypesByRegion.calls.count()).toBe(2);
       expect(command.dirty).toBeUndefined();
     });

   });

   describe('configureLoadBalancerOptions', function () {

     beforeEach(function() {

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
       expect(result).toEqual({ dirty: { }});
     });

     it('matches existing load balancers based on name - VPC', function() {
       this.command.vpcId = 'vpc-1';
       var result = service.configureLoadBalancerOptions(this.command);

       expect(this.command.loadBalancers).toEqual(['elb-1']);
       expect(result).toEqual({ dirty: { }});
     });

     it('sets dirty all unmatched load balancers - no VPC', function () {
       this.command.region = 'us-west-1';
       this.command.loadBalancers = ['elb-1', 'elb-2'];
       var result = service.configureLoadBalancerOptions(this.command);

       expect(this.command.loadBalancers).toEqual(['elb-2']);
       expect(result).toEqual({ dirty: { loadBalancers: ['elb-1']}});
     });

     it('sets dirty all unmatched load balancers - VPC', function () {
       this.command.loadBalancers = ['elb-1', 'elb-2'];
       this.command.vpcId = 'vpc-1';
       var result = service.configureLoadBalancerOptions(this.command);

       expect(this.command.loadBalancers).toEqual(['elb-1']);
       expect(result).toEqual({ dirty: { loadBalancers: ['elb-2']}});

       this.command.vpcId = 'vpc-2';
       result = service.configureLoadBalancerOptions(this.command);

       expect(this.command.loadBalancers).toEqual([]);
       expect(result).toEqual({ dirty: { loadBalancers: ['elb-1']}});
     });

     it('updates filteredData to new region - no VPC', function() {
       this.command.region = 'us-west-1';
       service.configureLoadBalancerOptions(this.command);
       expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-2']);
     });

     it('updates filteredData to new VPC', function() {
       this.command.vpcId = 'vpc-1';
       service.configureLoadBalancerOptions(this.command);
       expect(this.command.backingData.filtered.loadBalancers).toEqual(['elb-1']);
     });
   });

   describe('configureSecurityGroupOptions', function () {

     beforeEach(function() {
       this.allSecurityGroups = {
         test: {
           azure: {
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
             securityGroups: this.allSecurityGroups.test.azure['us-west-1']
           }
         },
         securityGroups: ['sg-1a', 'sg-2a'],
         credentials: 'test',
         region: 'us-west-1'
       };
     });

     it('matches existing security groups based on name - no VPC', function () {
       this.command.region = 'us-east-1';

       var result = service.configureSecurityGroupOptions(this.command);

       expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
       expect(result).toEqual({ dirty: {} });
     });

     it('matches existing security groups based on name - VPC', function() {
       this.command.vpcId = 'vpc-1';

       var result = service.configureSecurityGroupOptions(this.command);

       expect(this.command.securityGroups).toEqual(['sg-1va', 'sg-2va']);
       expect(result).toEqual({ dirty: { }});
     });

     it('matches on name or id, converting to id when name encountered', function() {
       this.command.securityGroups = ['sg1', 'sg-2a'];
       this.command.region = 'us-east-1';

       var result = service.configureSecurityGroupOptions(this.command);

       expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
       expect(result).toEqual({ dirty: {}});
     });

     it('sets dirty all unmatched security groups - no VPC', function () {
       this.command.securityGroups.push('sg-3a');
       this.command.region = 'us-east-1';

       var result = service.configureSecurityGroupOptions(this.command);

       expect(this.command.securityGroups).toEqual(['sg-1c', 'sg-2c']);
       expect(result).toEqual({ dirty: { securityGroups: ['sg3'] }});
     });

     it('sets dirty all unmatched security groups - VPC', function () {
       this.command.securityGroups.push('sg-3a');
       this.command.vpcId = 'vpc-2';

       var result = service.configureSecurityGroupOptions(this.command);

       expect(this.command.securityGroups).toEqual(['sg-3va']);
       expect(result).toEqual({ dirty: { securityGroups: ['sg1', 'sg2'] }});
     });

     it('updates filteredData to new region - no VPC', function() {
       var expected = this.allSecurityGroups.test.azure['us-east-1'].slice(0, 2);
       this.command.region = 'us-east-1';
       service.configureSecurityGroupOptions(this.command);
       expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
     });

     it('updates filteredData to new VPC', function() {
       var expected = this.allSecurityGroups.test.azure['us-west-1'].slice(3, 5);
       this.command.vpcId = 'vpc-1';
       service.configureSecurityGroupOptions(this.command);
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
       service.configureKeyPairs(this.command);
       expect(this.command.keyPair).toBe('shared');
     });

     it('retains keyPair when found in new region', function() {
       this.command.region = 'us-east-1';
       this.command.keyPair = 'test-pair';
       service.configureKeyPairs(this.command);
       expect(this.command.keyPair).toBe('test-pair');
     });

     it('marks dirty, sets to new default value when key pair not found in new account', function() {
       this.command.credentials = 'prod';
       this.command.keyPair = 'test-pair';
       var result = service.configureKeyPairs(this.command);
       expect(result.dirty.keyPair).toBe(true);
       expect(this.command.keyPair).toBe('prod-pair');
     });

     it('marks dirty, sets to default value when key pair not found in new region', function() {
       this.command.region = 'us-east-1';
       var result = service.configureKeyPairs(this.command);
       expect(result.dirty.keyPair).toBe(true);
       expect(this.command.keyPair).toBe('test-pair');
     });

   });

 });
