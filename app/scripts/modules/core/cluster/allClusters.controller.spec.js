'use strict';

describe('Controller: AllClustersCtrl', function () {

  const angular = require('angular');
  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./allClusters.controller.js'),
      require('../application/service/applications.read.service')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, applicationReader) {
      scope = $rootScope.$new();
      let application = {};
      applicationReader.addSectionToApplication({key: 'serverGroups', lazy: true}, application);
      controller = $controller('AllClustersCtrl', {
        $scope: scope,
        app: application,
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});


