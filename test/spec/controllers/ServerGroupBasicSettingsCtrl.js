'use strict';

describe('Controller: ServerGroupBasicSettings', function () {

  // load the controller's module
  beforeEach(module('deckApp'));

  var ctrl,
    scope;

  // Initialize the controller and a mock scope
  beforeEach(inject(function ($controller, $rootScope) {
    scope = $rootScope.$new();
    ctrl = $controller('ServerGroupBasicSettingsCtrl', {
      $scope: scope
    });
  }));

});
