'use strict';

describe('Controller: ServerGroupCapacitySelector', function () {

  beforeEach(
    window.module(
      require('./capacitySelector.directive.js')
    )
  );

  beforeEach(window.inject(function ($controller) {

    this.command = {
      capacity: {
        min: 0,
        max: 0,
        desired: 0
      },
      viewState: {
        useSimpleCapacity: false
      }
    };

    this.ctrl = $controller('awsServerGroupCapacitySelectorCtrl', {});
    this.ctrl.command = this.command;
  }));


  it('synchronizes capacity only when in simple capacity mode', function() {
    var command = this.command,
        ctrl = this.ctrl;

    command.viewState.useSimpleCapacity = true;
    command.capacity.desired = 2;
    ctrl.setMinMax(command.capacity.desired);

    expect(command.capacity.min).toBe(2);
    expect(command.capacity.max).toBe(2);

    command.viewState.useSimpleCapacity = false;
    command.capacity.desired = 1;
    ctrl.setMinMax(command.capacity.desired);

    expect(command.capacity.min).toBe(2);
    expect(command.capacity.max).toBe(2);

  });


});
