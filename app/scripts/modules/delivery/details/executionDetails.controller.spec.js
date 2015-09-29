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
  });

  describe('getException', function() {
    beforeEach(function() {
      this.$state = {};
      this.$stateParams = {
        application: 'app'
      };

      this.controller = this.$controller('executionDetails', {
        $scope: this.$scope,
        $stateParams: this.$stateParams,
        $state: this.$state,
        pipelineConfig: this.pipelineConfig,
      });
    });

    it('returns null when no stage context', function() {
      expect(this.controller.getException({})).toBe(null);
    });

    it('returns null when no kato.tasks field in stage context', function() {
      expect(this.controller.getException({context: {}})).toBe(null);
    });

    it('returns null when kato.tasks field in stage context is empty or not an array', function() {
      expect(this.controller.getException({context: { 'kato.tasks': 'not-array'}})).toBe(null);
      expect(this.controller.getException({context: { 'kato.tasks': []}})).toBe(null);
    });

    it('returns null when last kato task has no exception', function() {
      var stage = {
        context: {
          'kato.tasks': [
            {
              exception: {
                message: 'failed!'
              }
            },
            {

            }
          ]
        }
      };
      expect(this.controller.getException(stage)).toBe(null);
    });

    it('returns general exception if present', function() {
      expect(this.controller.getException({context: { 'exception': { 'details' : { 'errors': ['E1', 'E2']}}}})).toBe('E1, E2');
      expect(this.controller.getException({context: { 'exception': { 'details' : { 'errors': []}}}})).toBe(null);
      expect(this.controller.getException({context: { }})).toBe(null);
    });

    it('returns general exception even if a kato task is present', function() {
      var stage = {
        context: {
          'kato.tasks': [
            {
              exception: {
                message: 'failed!'
              }
            }
          ],
          exception: {
            details: {
              errors: ['E1', 'E2']
            }
          }
        }
      };
      expect(this.controller.getException(stage)).toBe('E1, E2');
    });

    it('returns exception when it is in the last kato task', function() {
      var stage = {
        context: {
          'kato.tasks': [
            {
              message: 'this one is fine'
            },
            {
              exception: {
                message: 'failed!'
              }
            }
          ]
        }
      };
      expect(this.controller.getException(stage)).toBe('failed!');
    });

  });

});
