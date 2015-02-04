'use strict';

describe('Controller: ExecutionDetails', function () {

  beforeEach(module('deckApp.executionDetails.controller'));

  beforeEach(inject(function ($controller, $rootScope, _) {
    this.$scope = $rootScope.$new();
    this.$controller = $controller;
    this._ = _;
  }));

  describe('getKatoException', function() {
    beforeEach(function() {
      this.$state = {};
      this.pipelineConfig = {};
      this.$stateParams = {
        application: 'app'
      };

      this.controller = this.$controller('executionDetails', {
        $scope: this.$scope,
        $stateParams: this.$stateParams,
        $state: this.$state,
        pipelineConfig: this.pipelineConfig,
        _: this._,
      });
    });

    it('returns null when no stage context', function() {
      expect(this.controller.getKatoException({})).toBe(null);
    });

    it('returns null when no kato.tasks field in stage context', function() {
      expect(this.controller.getKatoException({context: {}})).toBe(null);
    });

    it('returns null when kato.tasks field in stage context is empty or not an array', function() {
      expect(this.controller.getKatoException({context: { 'kato.tasks': 'not-array'}})).toBe(null);
      expect(this.controller.getKatoException({context: { 'kato.tasks': []}})).toBe(null);
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
      expect(this.controller.getKatoException(stage)).toBe(null);
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
      expect(this.controller.getKatoException(stage)).toBe('failed!');
    });

  });

});
