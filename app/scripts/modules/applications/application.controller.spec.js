'use strict';

describe('Controller: Application', function () {

  beforeEach(module('deckApp.application.controller'));

  beforeEach(inject(function ($controller, $rootScope) {
    this.scope = $rootScope.$new();

    this.application = {
      enableAutoRefresh: angular.noop,
      disableAutoRefresh: angular.noop,
      serverGroups: [
        { instances: {}}
      ]
    };

    this.initializeController = function() {
      this.ctrl = $controller('ApplicationCtrl', {
        $scope: this.scope,
        application: this.application,
      });
    };
  }));

  it('enables auto refresh when there are 500 or fewer instances in the application', function() {
    this.application.serverGroups[0].instances.length = 500;
    spyOn(this.application, 'enableAutoRefresh');
    spyOn(this.application, 'disableAutoRefresh');
    this.initializeController();

    expect(this.application.enableAutoRefresh).toHaveBeenCalled();
    expect(this.application.disableAutoRefresh).not.toHaveBeenCalled();

    this.application.serverGroups[0].instances.length = 1;
    this.initializeController();
    expect(this.application.disableAutoRefresh).not.toHaveBeenCalled();
  });

  it('disables auto refresh when there are more than 500 instances in the application', function() {
    this.application.serverGroups[0].instances.length = 0;
    spyOn(this.application, 'enableAutoRefresh');
    spyOn(this.application, 'disableAutoRefresh');
    this.initializeController();

    expect(this.application.enableAutoRefresh).toHaveBeenCalled();
    expect(this.application.disableAutoRefresh).not.toHaveBeenCalled();

    this.application.serverGroups[0].instances.length = 501;
    this.initializeController();
    expect(this.application.disableAutoRefresh).toHaveBeenCalled();
  });



});
