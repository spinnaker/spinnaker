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

describe('Service: awsServerGroup', function () {

  beforeEach(
    window.module(
      require('./serverGroupCommandBuilder.service.js')
    )
  );

  beforeEach(
    window.inject(function (_$httpBackend_, awsServerGroupCommandBuilder, _accountService_, _instanceTypeService_, _$q_,
                            _settings_, _subnetReader_, $rootScope) {
      this.$httpBackend = _$httpBackend_;
      this.service = awsServerGroupCommandBuilder;
      this.accountService = _accountService_;
      this.subnetReader = _subnetReader_;
      this.$q = _$q_;
      this.settings = _settings_;
      this.$scope = $rootScope;
      spyOn(_instanceTypeService_, 'getCategoryForInstanceType').and.returnValue(_$q_.when('custom'));
  }));

  describe('buildServerGroupCommandFromPipeline', function () {

    beforeEach(function() {

      this.cluster = {
        loadBalancers: ['elb-1'],
        account: 'prod',
        availabilityZones: {
          'us-west-1': ['d', 'g']
        },
        capacity: {
          min: 1,
          max: 1
        }
      };

      this.settings.providers.aws.defaults.account = 'test';
      this.settings.providers.aws.defaults.region = 'us-east-1';

      this.settings.preferredZonesByAccount = {
        test: {
          'us-west-1': ['a', 'b'],
          'us-east-1': ['d', 'e'],
        },
        prod: {
          'us-west-1': ['d', 'g'],
          'us-east-1': ['d', 'e'],
          'eu-west-1': ['a', 'm']
        },
        default: {
          'us-west-1': ['a', 'c'],
          'us-east-1': ['d', 'e'],
          'eu-west-1': ['a', 'm']
        }
      };

      spyOn(this.accountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(
        this.$q.when(['d', 'g'])
      );

      spyOn(this.accountService, 'getCredentialsKeyedByAccount').and.returnValue(
        this.$q.when({
          test: ['us-east-1', 'us-west-1'],
          prod: ['us-west-1', 'eu-west-1']
        })
      );

    });

    it('applies account, region from cluster', function () {

      var command = null;
      this.service.buildServerGroupCommandFromPipeline({}, this.cluster).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.credentials).toBe('prod');
      expect(command.region).toBe('us-west-1');
    });

    it('sets usePreferredZones', function() {
      var command = null;
      this.service.buildServerGroupCommandFromPipeline({}, this.cluster).then(function(result) {
        command = result;
      });

      this.$scope.$digest();
      expect(command.viewState.usePreferredZones).toBe(true);

      // remove an availability zone, should be false
      this.cluster.availabilityZones['us-west-1'].pop();
      this.service.buildServerGroupCommandFromPipeline({}, this.cluster).then(function(result) {
        command = result;
      });

      this.$scope.$digest();
      expect(command.viewState.usePreferredZones).toBe(false);
    });

  });

  describe('buildServerGroupCommandFromExisting', function () {

    beforeEach(function () {
      spyOn(this.accountService, 'getPreferredZonesByAccount').and.returnValue(this.$q.when([]));
      spyOn(this.subnetReader, 'listSubnets').and.returnValue(this.$q.when([]));
    });

    it('retains non-core suspended processes', function () {
      var serverGroup = {
        asg: {
          availabilityZones: [],
          vpczoneIdentifier: '',
          suspendedProcesses: [
            {processName: 'Launch'},
            {processName: 'Terminate'},
            {processName: 'AZRebalance'},
            {processName: 'AddToLoadBalancer'},
          ]
        }
      };
      var command = null;
      this.service.buildServerGroupCommandFromExisting({}, serverGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();
      expect(command.suspendedProcesses).toEqual(['AZRebalance']);
    });

    it('sets source capacity flags when creating for pipeline', function () {
      var serverGroup = {
        asg: {
          availabilityZones: [],
          vpczoneIdentifier: '',
          suspendedProcesses: []
        }
      };
      var command = null;
      this.service.buildServerGroupCommandFromExisting({}, serverGroup, 'editPipeline').then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.useSimpleCapacity).toBe(false);
      expect(command.useSourceCapacity).toBe(true);
    });
  });

});
