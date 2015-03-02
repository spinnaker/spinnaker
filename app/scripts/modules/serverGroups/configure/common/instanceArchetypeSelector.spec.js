'use strict';

describe('Controller: InstanceArchetypeSelectorCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module(
      'deckApp.serverGroup.configure.common',
      'deckApp.instanceType.service'
    )
  );

  beforeEach(
    inject(function ($rootScope, $controller) {
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

  describe('determine the instanceProfile from an instanceType', function () {

    it('should return "custom" if instance type is null', function () {
      var instanceType = null;
      var result = controller.determineInstanceProfileFromType(instanceType)

      expect(result).toBe('custom');
    });

    var customTypes = ['c1.large', 'c3.large', 'c4.large', 'm2.large'];
    customTypes.forEach( function(type) {
        it('should return "custom" if instance type is ' + type , function () {
          var result = controller.determineInstanceProfileFromType(type);

          expect(result).toBe('custom');
        });
      }
    );


    it('should return "micro" if the instance type is a t2.small', function () {
      var instanceType = 't2.small';
      var result = controller.determineInstanceProfileFromType(instanceType);

      expect(result).toBe('micro');
    });

    it('should return "general" if the instance type is a m3.large', function () {
      var instanceType = 'm3.large';
      var result = controller.determineInstanceProfileFromType(instanceType);

      expect(result).toBe('general');
    });

    it('should return "memory" if the instance type is a r3.small', function () {
      var instanceType = 'r3.small';
      var result = controller.determineInstanceProfileFromType(instanceType)

      expect(result).toBe('memory');
    });


  });
});
