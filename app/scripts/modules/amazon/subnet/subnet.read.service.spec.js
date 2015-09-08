'use strict';

describe('subnetReader', function() {

  var service, $http, $scope;

  beforeEach(
    window.module(
      require('./subnet.read.service.js')
    )
  );

  beforeEach(window.inject(function ($httpBackend, $rootScope, _subnetReader_) {
    service = _subnetReader_;
    $http = $httpBackend;
    $scope = $rootScope.$new();
  }));


  it('adds label to subnet, including (deprecated) if deprecated field is true', function () {

    $http.whenGET('/subnets').respond(200, [
      { purpose: 'internal', deprecated: true },
      { purpose: 'external', deprecated: false },
      { purpose: 'internal' },
    ]);

    var result = null;

    service.listSubnets().then(function(vpcs) { result = vpcs; });

    $http.flush();
    $scope.$digest();

    expect(result[0].label).toBe('internal (deprecated)');
    expect(result[0].deprecated).toBe(true);
    expect(result[1].label).toBe('external');
    expect(result[1].deprecated).toBe(false);
    expect(result[2].label).toBe('internal');
    expect(result[2].deprecated).toBe(false);
  });

});
