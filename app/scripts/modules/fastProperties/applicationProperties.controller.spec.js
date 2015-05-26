'use strict';

describe('Application Property Controller:', function () {
  var controller;

  beforeEach(module('spinnaker.applicationProperties.controller'));

  beforeEach(inject(function($controller, $rootScope){
    controller = $controller('ApplicationPropertiesController',{
      '$scope': $rootScope.$new(),
      'application': {
        registerAutoRefreshHandler: function() {}
      }
    });
  }));

  describe('Toggle FP Rollout details list', function () {

    var propId = 'foo|bar';
    var property = {id: propId};

    it('should add property id to openList if it is not present in the list', function () {
      expect(controller.openRolloutDetailsList).not.toContain(propId);

      controller.toggleRolloutDetails(property);

      expect(controller.openRolloutDetailsList).toContain(propId);
    });

    it('should remove property id from openList if it is present in the list', function () {
      controller.openRolloutDetailsList.push(propId);

      controller.toggleRolloutDetails(property);

      expect(controller.openRolloutDetailsList).not.toContain(propId);
    });

  });

});
