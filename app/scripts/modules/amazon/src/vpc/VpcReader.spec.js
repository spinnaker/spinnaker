'use strict';
import { mockHttpClient } from 'core/api/mock/jasmine';

import { API } from '@spinnaker/core';

import { VpcReader } from '../vpc/VpcReader';

describe('VpcReader', function () {
  var $httpBackend, $scope;

  beforeEach(
    window.inject(function (_$httpBackend_, $rootScope) {
      $httpBackend = _$httpBackend_;
      $scope = $rootScope.$new();
    }),
  );

  afterEach(function () {
    VpcReader.resetCache();
  });

  beforeEach(function () {
    $httpBackend.whenGET(API.baseUrl + '/networks/aws').respond(200, [
      { name: 'vpc1', id: 'vpc-1', deprecated: true },
      { name: 'vpc2', id: 'vpc-2', deprecated: false },
      { name: 'vpc3', id: 'vpc-3' },
    ]);
  });

  it('adds label to vpc, including (deprecated) if deprecated field is true', async function () {
    const http = mockHttpClient();
    var result = null;

    VpcReader.listVpcs().then(function (vpcs) {
      result = vpcs;
    });

    await http.flush();
    $scope.$digest();

    expect(result[0].label).toBe('vpc1 (deprecated)');
    expect(result[0].deprecated).toBe(true);
    expect(result[1].label).toBe('vpc2');
    expect(result[1].deprecated).toBe(false);
    expect(result[2].label).toBe('vpc3');
    expect(result[2].deprecated).toBe(false);
  });

  it('retrieves vpc name - not label - from id', async function () {
    const http = mockHttpClient();
    var result = null;

    VpcReader.getVpcName('vpc-1').then(function (name) {
      result = name;
    });

    await http.flush();
    $scope.$digest();

    expect(result).toBe('vpc1');

    VpcReader.getVpcName('vpc-2').then(function (name) {
      result = name;
    });

    $scope.$digest();

    expect(result).toBe('vpc2');

    VpcReader.getVpcName('vpc-4').then(function (name) {
      result = name;
    });

    $scope.$digest();

    expect(result).toBe(null);
  });
});
