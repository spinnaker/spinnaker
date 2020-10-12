import { mock, IHttpBackendService, IRootScopeService, IScope } from 'angular';

import { API } from 'core/api/ApiService';
import { SubnetReader } from 'core/subnet/subnet.read.service';
import { ISubnet } from 'core/domain';

describe('SubnetReader', function () {
  let $http: IHttpBackendService, $scope: IScope;

  beforeEach(
    mock.inject(function ($httpBackend: IHttpBackendService, $rootScope: IRootScopeService) {
      $http = $httpBackend;
      $scope = $rootScope.$new();
    }),
  );

  it('adds label to subnet, including (deprecated) if deprecated field is true', function () {
    $http
      .whenGET(API.baseUrl + '/subnets')
      .respond(200, [
        { purpose: 'internal', deprecated: true },
        { purpose: 'external', deprecated: false },
        { purpose: 'internal' },
      ]);

    let result: ISubnet[] = null;

    SubnetReader.listSubnets().then((subnets: ISubnet[]) => {
      result = subnets;
    });

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
