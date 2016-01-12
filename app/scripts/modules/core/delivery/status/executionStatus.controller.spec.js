'use strict';

describe('Controller: executionStatus', function () {

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./executionStatus.controller')
    )
  );

  describe('parameter extraction', function () {

    beforeEach(
      window.inject(function ($rootScope, $controller) {
        scope = $rootScope.$new();
        this.initialize = function(execution) {
          scope.execution = execution;
          controller = $controller('executionStatus', {
            $scope: scope
          });
        };
      })
    );

    it('adds parameters, sorted alphabetically, to scope if present on trigger', function () {
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

    it('does not add parameters to scope if none present in trigger', function () {
      var execution = { trigger: { } };
      this.initialize(execution);
      expect(controller.parameters).toBeUndefined();
    });
  });

});
