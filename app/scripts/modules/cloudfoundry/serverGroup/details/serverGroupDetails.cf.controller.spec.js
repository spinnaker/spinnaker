'use strict';

describe('Controller: cfServerGroupDetailsCtrl', function () {
  const angular = require('angular');

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./serverGroupDetails.cf.controller'),
      require('../../../core/application/service/applications.read.service')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, applicationReader) {
      scope = $rootScope.$new();
      let app = {};
      applicationReader.addSectionToApplication({key: 'serverGroups', lazy: true}, app);
      applicationReader.addSectionToApplication({key: 'loadBalancers', lazy: true}, app);
      controller = $controller('cfServerGroupDetailsCtrl', {
        $scope: scope,
        app: app,
        serverGroup: {}
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});

