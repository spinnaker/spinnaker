'use strict';

import { AccountService, SubnetReader } from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';

describe('Service: awsServerGroup', function () {
  beforeEach(window.module(require('./serverGroupCommandBuilder.service').name));

  beforeEach(
    window.inject(function (awsServerGroupCommandBuilder, _instanceTypeService_, _$q_, $rootScope) {
      this.service = awsServerGroupCommandBuilder;
      this.$q = _$q_;
      this.$scope = $rootScope;
      spyOn(_instanceTypeService_, 'getCategoryForInstanceType').and.returnValue(_$q_.when('custom'));
    }),
  );

  afterEach(AWSProviderSettings.resetToOriginal);

  describe('buildServerGroupCommandFromPipeline', function () {
    beforeEach(function () {
      this.cluster = {
        loadBalancers: ['elb-1'],
        account: 'prod',
        availabilityZones: {
          'us-west-1': ['d', 'g'],
        },
        capacity: {
          min: 1,
          max: 1,
        },
      };

      AWSProviderSettings.defaults = {
        account: 'test',
        region: 'us-east-1',
      };

      spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(this.$q.when(['d', 'g']));

      spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
        this.$q.when({
          test: ['us-east-1', 'us-west-1'],
          prod: ['us-west-1', 'eu-west-1'],
        }),
      );
    });

    it('applies account, region from cluster', function () {
      var command = null;
      this.service.buildServerGroupCommandFromPipeline({}, this.cluster).then(function (result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.credentials).toBe('prod');
      expect(command.region).toBe('us-west-1');
    });

    it('sets usePreferredZones', function () {
      var command = null;
      this.service.buildServerGroupCommandFromPipeline({}, this.cluster).then(function (result) {
        command = result;
      });

      this.$scope.$digest();
      expect(command.viewState.usePreferredZones).toBe(true);

      // remove an availability zone, should be false
      this.cluster.availabilityZones['us-west-1'].pop();
      this.service.buildServerGroupCommandFromPipeline({}, this.cluster).then(function (result) {
        command = result;
      });

      this.$scope.$digest();
      expect(command.viewState.usePreferredZones).toBe(false);
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    beforeEach(function () {
      spyOn(AccountService, 'getPreferredZonesByAccount').and.returnValue(this.$q.when([]));
      spyOn(SubnetReader, 'listSubnets').and.returnValue(this.$q.when([]));
    });

    it('retains non-core suspended processes', function () {
      var serverGroup = {
        asg: {
          availabilityZones: [],
          vpczoneIdentifier: '',
          suspendedProcesses: [
            { processName: 'Launch' },
            { processName: 'Terminate' },
            { processName: 'AZRebalance' },
            { processName: 'AddToLoadBalancer' },
          ],
        },
      };
      var command = null;
      this.service.buildServerGroupCommandFromExisting({}, serverGroup).then(function (result) {
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
          suspendedProcesses: [],
        },
      };
      var command = null;
      this.service.buildServerGroupCommandFromExisting({}, serverGroup, 'editPipeline').then(function (result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.useSimpleCapacity).toBe(false);
      expect(command.useSourceCapacity).toBe(true);
    });
  });
});
