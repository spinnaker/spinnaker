'use strict';

import {API_SERVICE} from 'core/api/api.service';

describe('Service: InstanceType', function () {

  let API;

  beforeEach(function() {
      window.module(
        require('./azureInstanceType.service'),
        API_SERVICE
      );
  });


  beforeEach(window.inject(function (_azureInstanceTypeService_, _$httpBackend_, _settings_, infrastructureCaches, _API_) {
    API = _API_;
    this.azureInstanceTypeService = _azureInstanceTypeService_;
    this.$httpBackend = _$httpBackend_;
    this.settings = _settings_;

    this.allTypes = [
      {account: 'test', region: 'us-west-2', name: 'm1.small', availabilityZone: 'us-west-2a'},
      {account: 'test', region: 'us-west-2', name: 'm2.xlarge', availabilityZone: 'us-west-2b'},
      {account: 'test', region: 'eu-west-1', name: 'hs1.8xlarge', availabilityZone: 'eu-west-1c'},
      {account: 'test', region: 'eu-west-1', name: 'm2.xlarge', availabilityZone: 'eu-west-1c'},
    ];

    infrastructureCaches.createCache('instanceTypes', {});
    if (infrastructureCaches.get('instanceTypes')) {
      infrastructureCaches.get('instanceTypes').removeAll();
    }
  }));

  afterEach(function () {
    this.$httpBackend.verifyNoOutstandingRequest();
  });

  describe('getAllTypesByRegion', function () {

    it('returns types, indexed by region', function () {

      this.$httpBackend.expectGET(API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null;
      this.azureInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = result;
      });

      this.$httpBackend.flush();
      expect(results['us-west-2'].length).toBe(2);
      expect(_.map(results['us-west-2'], 'name').sort()).toEqual(['m1.small', 'm2.xlarge']);
    });

  });

  describe('getAvailableTypesForRegions', function() {

    it('returns results for a single region', function() {
      this.$httpBackend.expectGET(API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.azureInstanceTypeService;

      this.azureInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-2']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual(['m1.small', 'm2.xlarge']);
    });

    it('returns empty list for region with no instance types', function() {
      this.$httpBackend.expectGET(API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.azureInstanceTypeService;

      this.azureInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-3']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual([]);
    });

    it('returns an intersection when multiple regions are provided', function() {
      this.$httpBackend.expectGET(API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.azureInstanceTypeService;

      this.azureInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-2', 'eu-west-1']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual(['m2.xlarge']);
    });

  });

});
