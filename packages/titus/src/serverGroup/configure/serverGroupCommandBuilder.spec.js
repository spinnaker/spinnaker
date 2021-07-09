'use strict';

import { NameUtils } from 'core/naming';

import { TitusProviderSettings } from '../../titus.settings';

describe('titusServerGroupCommandBuilder', function () {
  beforeEach(window.module(require('./ServerGroupCommandBuilder').name));

  beforeEach(
    window.inject(function (titusServerGroupCommandBuilder, $q, $rootScope) {
      this.titusServerGroupCommandBuilder = titusServerGroupCommandBuilder;
      this.$scope = $rootScope;
      this.$q = $q;
    }),
  );

  afterEach(TitusProviderSettings.resetToOriginal);

  describe('buildNewServerGroupCommand', function () {
    it('should initializes to default values', function () {
      var command = null;
      TitusProviderSettings.defaults.iamProfile = '{{application}}InstanceProfile';
      this.titusServerGroupCommandBuilder.buildNewServerGroupCommand({ name: 'titusApp' }).then(function (result) {
        command = result;
      });

      this.$scope.$digest();
      expect(command.iamProfile).toBe('titusAppInstanceProfile');
      expect(command.viewState.mode).toBe('create');
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('should set iam profile if available otherwise use the default', function () {
      spyOn(NameUtils, 'parseServerGroupName').and.returnValue(this.$q.when('titusApp-test-test'));

      var baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        cluster: 'titus-test-test',
        type: 'titus',
        loudProvider: 'titus',
        iamProfile: 'titusAppInstanceProfile',
        resources: {},
        capacity: {},
        image: {},
      };

      var command = null;
      this.titusServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'titusApp' }, baseServerGroup)
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();

      expect(command.iamProfile).toBe('titusAppInstanceProfile');
      expect(command.viewState.mode).toBe('clone');
    });
  });
});
