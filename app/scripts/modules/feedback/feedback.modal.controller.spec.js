'use strict';

describe('Controller: FeedbackModalCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module('spinnaker.feedback.modal.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('FeedbackModalCtrl', {
        $scope: scope,
        $modalInstance: {}
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});

