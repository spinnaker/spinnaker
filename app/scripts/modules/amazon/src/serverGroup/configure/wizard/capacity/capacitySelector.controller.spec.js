'use strict';

import { CAPACITY_SELECTOR } from './capacitySelector.component';

describe('Controller: ServerGroupCapacitySelector', function () {

  beforeEach(
    window.module(
      CAPACITY_SELECTOR
    )
  );

  beforeEach(window.inject(function ($componentController) {

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

    this.ctrl = $componentController('awsServerGroupCapacitySelector', {});
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
