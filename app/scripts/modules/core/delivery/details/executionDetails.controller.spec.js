'use strict';

describe('Controller: ExecutionDetails', function () {

  beforeEach(
    window.module(
      require('./executionDetails.controller'),
      function ($provide) {
        $provide.service('pipelineConfig', function () {
          return {
            getStageConfig: function (stage) {
              return stage;
            }
          };
        });
      }
    )
  );

  beforeEach(window.inject(function ($controller, $rootScope, pipelineConfig) {
    this.$scope = $rootScope.$new();
    this.$controller = $controller;
    this.pipelineConfig = pipelineConfig;
  }));

  describe('isRestartable', function() {
    beforeEach(function() {
      this.controller = this.$controller('executionDetails', {
        $scope: this.$scope,
      });
      this.controller.execution = {
        isRunning: false
      };
      this.controller.application = {
        attributes: {
          enableRestartRunningExecutions: false
        }
      };
    });

    it('returns false when no stage config', function() {
      expect(this.controller.isRestartable()).toBe(false);
    });

    it('returns false when stage is not restartable', function() {
      expect(this.controller.isRestartable({restartable: false})).toBe(false);
    });

    it('returns false when stage is already restarting', function() {
      expect(this.controller.isRestartable({restartable: true, isRestarting: true})).toBe(false);
    });

    it('returns true when stage is restartable', function() {
      expect(this.controller.isRestartable({restartable: true})).toBe(true);
    });

    it('returns true when stage is running, is restartable and enableRestartRunningExecutions=true', function() {
      this.controller.execution.isRunning = true;
      this.controller.application.attributes.enableRestartRunningExecutions = true;
      this.$scope.$digest();
      expect(this.controller.isRestartable({restartable: true})).toBe(true);
    });
  });

});
