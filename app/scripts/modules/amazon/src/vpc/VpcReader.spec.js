'use strict';
import { mockHttpClient } from 'core/api/mock/jasmine';
import { VpcReader } from '../vpc/VpcReader';

describe('VpcReader', function () {
  var $scope;

  beforeEach(
    window.inject(function ($rootScope) {
      $scope = $rootScope.$new();
    }),
  );

  afterEach(function () {
    VpcReader.resetCache();
  });

  it('adds label to vpc, including (deprecated) if deprecated field is true', async function () {
    const http = mockHttpClient({ autoFlush: true });
    http.expectGET('/networks/aws').respond(200, [
      { name: 'vpc1', id: 'vpc-1', deprecated: true },
      { name: 'vpc2', id: 'vpc-2', deprecated: false },
      { name: 'vpc3', id: 'vpc-3' },
    ]);

    const vpcs = await VpcReader.listVpcs();

    expect(vpcs[0].label).toBe('vpc1 (deprecated)');
    expect(vpcs[0].deprecated).toBe(true);
    expect(vpcs[1].label).toBe('vpc2');
    expect(vpcs[1].deprecated).toBe(false);
    expect(vpcs[2].label).toBe('vpc3');
    expect(vpcs[2].deprecated).toBe(false);
  });

  it('retrieves vpc name - not label - from id', async function () {
    const http = mockHttpClient({ autoFlush: true });
    http.expectGET('/networks/aws').respond(200, [
      { name: 'vpc1', id: 'vpc-1', deprecated: true },
      { name: 'vpc2', id: 'vpc-2', deprecated: false },
      { name: 'vpc3', id: 'vpc-3' },
    ]);

    const vpc1 = await VpcReader.getVpcName('vpc-1');
    expect(vpc1).toBe('vpc1');

    const vpc2 = await VpcReader.getVpcName('vpc-2');
    expect(vpc2).toBe('vpc2');

    const vpc4 = await VpcReader.getVpcName('vpc-4');
    expect(vpc4).toBe(null);
  });
});
