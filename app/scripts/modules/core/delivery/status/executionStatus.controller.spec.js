'use strict';

describe('Controller: executionStatus', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./executionStatus.controller')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('executionStatus', {
        $scope: scope
      });
    })
  );

  describe('getSuspendedStage', function() {
    it('returns first suspended stage summary', function() {
      var execution = {
        stageSummaries: [
          { isSuspended: false, name: 'not-suspended' },
          { isSuspended: true, name: 'is-suspended' },
          { isSuspended: true, name: 'is-also-suspended' },
        ]
      };
      expect(controller.getSuspendedStage(execution)).toBe('is-suspended');
    });

    it('returns "Unknown" when no stage is suspended', function() {
      var execution = {
        stageSummaries: [
          { isSuspended: false, name: 'not-suspended' },
          { isSuspended: false, name: 'also-not-suspended' },
        ]
      };
      expect(controller.getSuspendedStage(execution)).toBe('Unknown');
    });

    it('returns "Unknown" when no stage is suspended', function() {
      var execution = {
        stageSummaries: [
          { isSuspended: false, name: 'not-suspended' },
          { isSuspended: true, name: 'is-suspended' },
          { isSuspended: false, name: 'also-not-suspended' },
        ]
      };
      expect(controller.getSuspendedStage(execution)).toBe('is-suspended');
    });
  });

});
