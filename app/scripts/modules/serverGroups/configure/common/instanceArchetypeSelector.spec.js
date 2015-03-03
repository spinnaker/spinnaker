'use strict';

describe('Controller: InstanceArchetypeSelectorCtrl', function () {

  var controller;
  var scope;
  var instanceTypeService;
  var generalFamily = {
    type: 'general',
    families: [{
      instanceTypes: [
        {name: 'm3.medium'},
        {name: 'm3.large'},
        {name: 'm3.xlarge'},
        {name: 'm3.2xlarge'}
      ]
    }]
  };

  var memoryFamily = {
    type: 'memory',
    families: [{
      instanceTypes: [
        {name: 'r3.large'},
        {name: 'r3.xlarge'},
        {name: 'r3.2xlarge'},
        {name: 'r3.4xlarge'},
      ]
    }]
  };

  var microFamily = {
    type: 'micro',
    families: [{
      instanceTypes: [
        {name: 't2.small'},
        {name: 't2.medium'},
      ]
    }]
  };

  var categories = [generalFamily, memoryFamily, microFamily];


  beforeEach(
    module(
      'deckApp.serverGroup.configure.common',
      'deckApp.instanceType.service'
    )
  );

  beforeEach(
    inject(function ($rootScope, $controller, _instanceTypeService_) {
      instanceTypeService = _instanceTypeService_
      scope = $rootScope.$new();
      scope.command = {
        selectedProvider: {}
      };

      controller = $controller('InstanceArchetypeSelectorCtrl', {
        $scope: scope
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });

  describe('find profile name for instance type', function () {

    generalFamily.families[0].instanceTypes.forEach( function(instanceType) {
      it('should return "general" if the ' + instanceType.name + ' is in the "general" category', function () {
        var result = controller.findProfileForInstanceType(categories, instanceType.name);
        expect(result).toBe('general');
      });
    });

    memoryFamily.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "memory" if the ' + instanceType.name + ' is in the "memory" category', function () {
        var result = controller.findProfileForInstanceType(categories, instanceType.name);
        expect(result).toBe('memory');
      });
    });

    microFamily.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "micro" if the ' + instanceType.name + ' is in the "micro" category', function () {
        var result = controller.findProfileForInstanceType(categories, instanceType.name);
        expect(result).toBe('micro');
      });
    });


    var customTypes = ['c1.large', 'c3.large', 'c4.large', 'm2.large'];
    customTypes.forEach(function (instanceType) {
      it('should return "custom" if the ' + instanceType + ' is not in a category', function () {
        var result = controller.findProfileForInstanceType(categories, instanceType);
        expect(result).toBe('custom');
      });
    });

  });

});
