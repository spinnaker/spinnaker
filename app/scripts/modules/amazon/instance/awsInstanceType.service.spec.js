'use strict';

import {API_SERVICE} from 'core/api/api.service';

describe('Service: InstanceType', function () {

  let API;

  beforeEach(function() {
      window.module(
        require('./awsInstanceType.service'),
        API_SERVICE
      );
  });


  beforeEach(window.inject(function (_awsInstanceTypeService_, _$httpBackend_, _settings_, _API_, infrastructureCaches) {
    API = _API_;
    this.awsInstanceTypeService = _awsInstanceTypeService_;
    this.$httpBackend = _$httpBackend_;
    this.settings = _settings_;

    this.allTypes = [
      {account: 'test', region: 'us-west-2', name: 'm1.small', availabilityZone: 'us-west-2a'},
      {account: 'test', region: 'us-west-2', name: 'm2.xlarge', availabilityZone: 'us-west-2b'},
      {account: 'test', region: 'eu-west-1', name: 'hs1.8xlarge', availabilityZone: 'eu-west-1c'},
      {account: 'test', region: 'eu-west-1', name: 'm2.xlarge', availabilityZone: 'eu-west-1c'},
      {account: 'test', region: 'us-east-1', name: 'm4.2xlarge', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 't2.nano', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 't2.micro', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 'm4.xlarge', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 'm4.4xlarge', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 't2.medium', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 'm4.large', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 'm4.16xlarge', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 'm4.10xlarge', availabilityZone: 'us-east-1c'},
      {account: 'test', region: 'us-east-1', name: 't2.loltiny', availabilityZone: 'us-east-1c'},
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

      this.$httpBackend.expectGET( API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null;
      this.awsInstanceTypeService.getAllTypesByRegion().then(function(result) {
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
          service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-2']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual(['m1.small', 'm2.xlarge']);
    });

    it('returns empty list for region with no instance types', function() {
      this.$httpBackend.expectGET(API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-3']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual([]);
    });

    it('returns an intersection when multiple regions are provided', function() {
      this.$httpBackend.expectGET(API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-2', 'eu-west-1']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual(['m2.xlarge']);
    });

    it('filters instance types by VPC and virtualization type', function () {
      let types = ['c4.a', 'c3.a', 'c4.a', 'c1.a'];
      let service = this.awsInstanceTypeService;
      expect(service.filterInstanceTypes(types, 'hvm', true)).toEqual(['c4.a', 'c3.a', 'c4.a']);
      expect(service.filterInstanceTypes(types, 'hvm', false)).toEqual(['c3.a']);
      expect(service.filterInstanceTypes(types, 'paravirtual', true)).toEqual(['c3.a', 'c1.a']);
      expect(service.filterInstanceTypes(types, 'paravirtual', false)).toEqual(['c3.a', 'c1.a']);
    });

    it('assumes HVM is supported for unknown families', function () {
      let types = ['c400.a', 'c300.a', 'c3.a', 'c1.a'];
      let service = this.awsInstanceTypeService;
      expect(service.filterInstanceTypes(types, 'hvm', true)).toEqual(['c400.a', 'c300.a', 'c3.a']);
    });

    it('sorts instance types by family then class size', function() {
      this.$httpBackend.expectGET(API.baseUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions(result, ['us-east-1']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual([
        'm4.large',
        'm4.xlarge',
        'm4.2xlarge',
        'm4.4xlarge',
        'm4.10xlarge',
        'm4.16xlarge',
        't2.nano',
        't2.micro',
        't2.medium',
        't2.loltiny'
      ]);
    });

  });

});
