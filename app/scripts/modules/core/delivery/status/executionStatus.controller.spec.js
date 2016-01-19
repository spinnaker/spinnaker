'use strict';

describe('Controller: executionStatus', function () {

  var controller;

  beforeEach(
    window.module(
      require('./executionStatus.directive')
    )
  );

  describe('parameter extraction', function () {

    beforeEach(
      window.inject(function ($controller) {
        this.initialize = function(execution) {
          controller = $controller('executionStatus', {}, { execution: execution });
        };
      })
    );

    it('adds parameters, sorted alphabetically, to vm if present on trigger', function () {
      var execution = {
        trigger: {
          parameters: {
            a: 'b',
            b: 'c',
            d: 'a',
          }
        }
      };
      this.initialize(execution);
      expect(controller.parameters).toEqual([
        {key: 'a', value: 'b'},
        {key: 'b', value: 'c'},
        {key: 'd', value: 'a'}
      ]);
    });

    it('does not add parameters to vm if none present in trigger', function () {
      var execution = { trigger: { } };
      this.initialize(execution);
      expect(controller.parameters).toBeUndefined();
    });

    it('excludes some parameters if the pipeline is a strategy', function () {
      var execution = {
        isStrategy: true,
        trigger: {
          parameters: {
            included: 'a',
            parentPipelineId: 'b',
            strategy: 'c',
            parentStageId: 'd',
            deploymentDetails: 'e',
            cloudProvider: 'f'
          }
        }
      };
      this.initialize(execution);
      expect(controller.parameters).toEqual([
        {key: 'included', value: 'a'}
      ]);
    });
  });

});
