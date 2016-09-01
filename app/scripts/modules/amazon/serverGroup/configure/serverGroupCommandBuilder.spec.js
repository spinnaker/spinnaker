'use strict';

describe('awsServerGroupCommandBuilder', function() {
  const AccountServiceFixture = require('../../../../../../test/fixture/AccountServiceFixtures');

  beforeEach(
    window.module(
      require('./serverGroupCommandBuilder.service.js')
    )
  );

  beforeEach(window.inject(function(awsServerGroupCommandBuilder, accountService, $q, $rootScope, subnetReader, instanceTypeService, _settings_) {
    this.awsServerGroupCommandBuilder = awsServerGroupCommandBuilder;
    this.$scope = $rootScope;
    this.instanceTypeService = instanceTypeService;
    this.$q = $q;
    this.settings = _settings_;
    spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when(AccountServiceFixture.preferredZonesByAccount));
    spyOn(accountService, 'getCredentialsKeyedByAccount').and.returnValue($q.when(AccountServiceFixture.credentialsKeyedByAccount));
    spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
    spyOn(accountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(
      this.$q.when(['a', 'b', 'c'])
    );
  }));

  describe('buildNewServerGroupCommand', function() {

    it('initializes to default values, setting usePreferredZone flag to true', function () {
      var command = null;
      this.settings.providers.aws.defaults.iamRole = '{{application}}IAMRole';
      this.awsServerGroupCommandBuilder.buildNewServerGroupCommand({name: 'appo', defaultCredentials: {}, defaultRegions: {}}, 'aws').then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['a', 'b', 'c']);
      expect(command.iamRole).toBe('appoIAMRole');
    });


  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('sets usePreferredZones flag based on initial value', function() {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('custom'));
      var baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
        },
      };
      var command = null;

      this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['g', 'h', 'i']);

      baseServerGroup.asg.availabilityZones = ['g'];

      this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(false);
      expect(command.availabilityZones).toEqual(['g']);

    });

    it('sets profile and instance type if available', function() {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      var baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
        },
        launchConfig: {
          instanceType: 'something-custom',
          instanceMonitoring: {},
          securityGroups: [],
        },
      };
      var command = null;

      this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.instanceProfile).toBe('selectedProfile');
      expect(command.instanceType).toBe('something-custom');
    });

    it('copies suspended processes unless the mode is "editPipeline"', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      var baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
          suspendedProcesses: [ {processName: 'x'}, {processName: 'a'}]
        },
        launchConfig: {
          instanceType: 'something-custom',
          instanceMonitoring: {},
          securityGroups: [],
        },
      };
      var command = null;

      this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup)
        .then((result) => command = result);

      this.$scope.$digest();

      expect(command.suspendedProcesses).toEqual(['x', 'a']);

      this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup, 'editPipeline')
        .then((result) => command = result);

      this.$scope.$digest();

      expect(command.suspendedProcesses).toEqual([]);
    });
  });

});
