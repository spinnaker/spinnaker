'use strict';

describe('Service: instanceTypeService', function () {

  var instanceTypeService, $q, $scope;

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
    window.module(require('./instanceTypeService.js'))
  );

  beforeEach(
    window.inject(function (_instanceTypeService_, _$q_, $rootScope, serviceDelegate) {
      instanceTypeService = _instanceTypeService_;
      $q = _$q_;
      $scope = $rootScope.$new();

      spyOn(serviceDelegate, 'getDelegate').and.returnValue({
        getCategories: () => { return $q.when(categories); }
      });
    })
  );

  describe('find profile name for instance type', function () {

    beforeEach(function() {
      spyOn(instanceTypeService, 'getCategories').and.returnValue($q.when(categories));
    });

    generalFamily.families[0].instanceTypes.forEach( function(instanceType) {
      it('should return "general" if the ' + instanceType.name + ' is in the "general" category', function () {
        var result = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType.name).then(function(category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('general');
      });
    });

    memoryFamily.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "memory" if the ' + instanceType.name + ' is in the "memory" category', function () {
        var result = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType.name).then(function(category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('memory');
      });
    });

    microFamily.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "micro" if the ' + instanceType.name + ' is in the "micro" category', function () {
        var result = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType.name).then(function(category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('micro');
      });
    });


    var customTypes = ['c1.large', 'c3.large', 'c4.large', 'm2.large'];
    customTypes.forEach(function (instanceType) {
      it('should return "custom" if the ' + instanceType + ' is not in a category', function () {
        var result = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType).then(function(category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('custom');
      });
    });

  });
});


