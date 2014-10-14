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

  it('initSelection should use amiName or allImageSelection if present, otherwise leave blank', function () {
    var result = null,
        blankSelection = {id: '', text: ''};

    var callback = function(value) {
      result = value;
    };

    scope.select2Params.initSelection(null, callback);
    expect(result).toEqual(blankSelection);

    scope.command = {};
    scope.select2Params.initSelection(null, callback);
    expect(result).toEqual(blankSelection);

    scope.command.amiName = 'someAmi';
    scope.select2Params.initSelection(null, callback);
    expect(result).toEqual({id: 'someAmi', text: 'someAmi'});

    delete scope.command.amiName;
    scope.command.allImageSelection = 'someImageSelection';
    scope.select2Params.initSelection(null, callback);
    expect(result).toEqual({id: 'someImageSelection', text: 'someImageSelection'});
  });
});
