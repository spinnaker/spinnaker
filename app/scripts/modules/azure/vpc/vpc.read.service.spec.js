'use strict';

describe('vpcReader', function() {

  var service, $http, $scope;

  beforeEach(
    window.module(
      require('./vpc.read.service.js')
    )
  );

  beforeEach(window.inject(function ($httpBackend, $rootScope, _azureVpcReader_) {
    service = _azureVpcReader_;
    $http = $httpBackend;
    $scope = $rootScope.$new();
  }));

  beforeEach(function() {
    $http.whenGET('/vpcs').respond(200, [
      { name: 'vpc1', id: 'vpc-1', deprecated: true },
      { name: 'vpc2', id: 'vpc-2', deprecated: false },
      { name: 'vpc3', id: 'vpc-3' },
    ]);
  });


  it('adds label to vpc, including (deprecated) if deprecated field is true', function () {

    var result = null;

    service.listVpcs().then(function(vpcs) { result = vpcs; });

    $http.flush();
    $scope.$digest();

    expect(result[0].label).toBe('vpc1 (deprecated)');
    expect(result[0].deprecated).toBe(true);
    expect(result[1].label).toBe('vpc2');
    expect(result[1].deprecated).toBe(false);
    expect(result[2].label).toBe('vpc3');
    expect(result[2].deprecated).toBe(false);
  });

  it('retrieves vpc name - not label - from id', function() {

    var result = null;

    service.getVpcName('vpc-1').then(function(name) { result = name; });

    $http.flush();
    $scope.$digest();

    expect(result).toBe('vpc1');

    service.getVpcName('vpc-2').then(function(name) { result = name; });

    $http.flush();
    $scope.$digest();

    expect(result).toBe('vpc2');

    service.getVpcName('vpc-4').then(function(name) { result = name; });

    $http.flush();
    $scope.$digest();

    expect(result).toBe(null);
  });

});
