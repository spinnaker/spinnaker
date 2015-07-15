'use strict';


describe('Controller: Application', function () {

  const angular = require('angular');

  beforeEach(window.module('spinnaker.application.controller'));

  beforeEach(window.inject(function ($controller, $rootScope) {
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


});
