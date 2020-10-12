'use strict';

import { AccountService } from '@spinnaker/core';

describe('dcosServerGroupCommandBuilder', function () {
  beforeEach(window.module(require('./CommandBuilder').name));

  beforeEach(
    window.inject(function (dcosServerGroupCommandBuilder, $q, $rootScope) {
      this.dcosServerGroupCommandBuilder = dcosServerGroupCommandBuilder;
      this.$scope = $rootScope;
      this.$q = $q;
      spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue($q.when({ test: {} }));
    }),
  );

  describe('buildNewServerGroupCommand', function () {
    it('should initialize to default values', function () {
      var command = null;
      this.dcosServerGroupCommandBuilder
        .buildNewServerGroupCommand({ name: 'dcosApp', accounts: ['test'] })
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();
      expect(command.viewState.mode).toBe('create');
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('should use base server group otherwise use the default', function () {
      var baseServerGroup = {};
      baseServerGroup.deployDescription = {
        account: 'test-account',
        region: 'cluster1/foo/bar',
        cluster: 'dcos-test-test',
        type: 'dcos',
        cloudProvider: 'dcos',
        resources: {},
        capacity: {},
        image: {},
      };

      var command = null;
      this.dcosServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'dcosApp' }, baseServerGroup)
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();

      expect(command.viewState.mode).toBe('clone');
    });
  });
});
