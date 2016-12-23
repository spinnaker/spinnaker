'use strict';

describe('titusServerGroupCommandBuilder', function() {

  beforeEach(
    window.module(
      require('./ServerGroupCommandBuilder.js')
    )
  );

  beforeEach(window.inject(function(titusServerGroupCommandBuilder, namingService, $q, $rootScope, _settings_) {
    this.titusServerGroupCommandBuilder = titusServerGroupCommandBuilder;
    this.$scope = $rootScope;
    this.$q = $q;
    this.settings = _settings_;
    this.namingService = namingService;
  }));

  describe('buildNewServerGroupCommand', function() {
    it('should initializes to default values', function () {
      var command = null;
      this.settings.providers.titus.defaults.iamProfile = '{{application}}InstanceProfile';
      this.titusServerGroupCommandBuilder.buildNewServerGroupCommand({ name: 'titusApp' }).then(function(result) {
        command = result;
      });

      this.$scope.$digest();
      expect(command.iamProfile).toBe('titusAppInstanceProfile');
      expect(command.viewState.mode).toBe('create');
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('should set iam profile if available otherwise use the default', function() {
      spyOn(this.namingService, 'parseServerGroupName').and.returnValue(this.$q.when('titusApp-test-test'));

      var baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        cluster: 'titus-test-test',
        type: 'titus',
        loudProvider: 'titus',
        iamProfile: 'titusAppInstanceProfile',
        resources: {},
        capacity: {},
        image: {}
      };

      var command = null;
      this.titusServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'titusApp'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.iamProfile).toBe('titusAppInstanceProfile');
      expect(command.viewState.mode).toBe('clone');
    });
  });

});
