'use strict';

describe('Controller: ServerGroupCapacity', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(module('deckApp'));

  beforeEach(inject(function ($controller, $rootScope, modalWizardService) {
    this.scope = $rootScope.$new();

    this.scope.state = {
      useSimpleCapacity: false
    };

    this.scope.command = {
      capacity: {
        min: 0,
        max: 0,
        desired: 0
      }
    };

    this.wizard = jasmine.createSpyObj('wizard', ['markComplete', 'markClean', 'markDirty']);

    spyOn(modalWizardService, 'getWizard').andReturn(this.wizard);

    this.ctrl = $controller('ServerGroupCapacityCtrl', {
      $scope: this.scope,
      modalWizardService: modalWizardService
    });
  }));


  it('synchronizes capacity only when in simple capacity mode', function() {
    var scope = this.scope;

    scope.state.useSimpleCapacity = true;
    scope.command.capacity.desired = 2;
    scope.$digest();

    expect(scope.command.capacity.min).toBe(2);
    expect(scope.command.capacity.max).toBe(2);

    scope.state.useSimpleCapacity = false;
    scope.command.capacity.desired = 1;
    scope.$digest();

    expect(scope.command.capacity.min).toBe(2);
    expect(scope.command.capacity.max).toBe(2);

  });


});
