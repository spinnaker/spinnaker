'use strict';
import { mockHttpClient } from 'core/api/mock/jasmine';

describe('Service: InstanceType', function () {
  beforeEach(function () {
    window.module(require('./awsInstanceType.service').name);
  });

  beforeEach(
    window.inject(function (_awsInstanceTypeService_) {
      this.awsInstanceTypeService = _awsInstanceTypeService_;

      this.allTypes = [
        { account: 'test', region: 'us-west-2', name: 'm1.small', availabilityZone: 'us-west-2a' },
        { account: 'test', region: 'us-west-2', name: 'm2.xlarge', availabilityZone: 'us-west-2b' },
        { account: 'test', region: 'eu-west-1', name: 'hs1.8xlarge', availabilityZone: 'eu-west-1c' },
        { account: 'test', region: 'eu-west-1', name: 'm2.xlarge', availabilityZone: 'eu-west-1c' },
        { account: 'test', region: 'us-east-1', name: 'm4.2xlarge', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 't2.nano', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 't2.micro', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 'm4.xlarge', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 'm4.4xlarge', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 't2.medium', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 'm4.large', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 'm4.16xlarge', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 'm4.10xlarge', availabilityZone: 'us-east-1c' },
        { account: 'test', region: 'us-east-1', name: 't2.loltiny', availabilityZone: 'us-east-1c' },
      ];
    }),
  );

  describe('getAllTypesByRegion', function () {
    it('returns types, indexed by region', async function () {
      const http = mockHttpClient();
      http.expectGET('/instanceTypes').respond(200, this.allTypes);

      let results = null;
      this.awsInstanceTypeService.getAllTypesByRegion().then(function (result) {
        results = result;
      });

      await http.flush();
      expect(results['us-west-2'].length).toBe(2);
      expect(_.map(results['us-west-2'], 'name').sort()).toEqual(['m1.small', 'm2.xlarge']);
    });
  });

  describe('getAvailableTypesForRegions', function () {
    it('returns results for a single region', async function () {
      const http = mockHttpClient();
      http.expectGET('/instanceTypes').respond(200, this.allTypes);

      let results = null,
        service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function (result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-2']);
      });

      await http.flush();
      expect(results).toEqual(['m1.small', 'm2.xlarge']);
    });

    it('returns empty list for region with no instance types', async function () {
      const http = mockHttpClient();
      http.expectGET('/instanceTypes').respond(200, this.allTypes);

      let results = null,
        service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function (result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-3']);
      });

      await http.flush();
      expect(results).toEqual([]);
    });

    it('returns an intersection when multiple regions are provided', async function () {
      const http = mockHttpClient();
      http.expectGET('/instanceTypes').respond(200, this.allTypes);

      let results = null,
        service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function (result) {
        results = service.getAvailableTypesForRegions(result, ['us-west-2', 'eu-west-1']);
      });

      await http.flush();
      expect(results).toEqual(['m2.xlarge']);
    });

    it('filters instance types by VPC and virtualization type', function () {
      const types = ['c4.a', 'c3.a', 'c4.a', 'c1.a'];
      const service = this.awsInstanceTypeService;
      expect(service.filterInstanceTypes(types, 'hvm', true)).toEqual(['c4.a', 'c3.a', 'c4.a']);
      expect(service.filterInstanceTypes(types, 'hvm', false)).toEqual(['c3.a']);
      expect(service.filterInstanceTypes(types, 'paravirtual', true)).toEqual(['c3.a', 'c1.a']);
      expect(service.filterInstanceTypes(types, 'paravirtual', false)).toEqual(['c3.a', 'c1.a']);
    });

    it('assumes HVM is supported for unknown families', function () {
      const types = ['c400.a', 'c300.a', 'c3.a', 'c1.a'];
      const service = this.awsInstanceTypeService;
      expect(service.filterInstanceTypes(types, 'hvm', true)).toEqual(['c400.a', 'c300.a', 'c3.a']);
    });

    it('sorts instance types by family then class size', async function () {
      const http = mockHttpClient();
      http.expectGET('/instanceTypes').respond(200, this.allTypes);

      let results = null,
        service = this.awsInstanceTypeService;

      this.awsInstanceTypeService.getAllTypesByRegion().then(function (result) {
        results = service.getAvailableTypesForRegions(result, ['us-east-1']);
      });

      await http.flush();
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
        't2.loltiny',
      ]);
    });
  });

  describe('isBurstingSupported', function () {
    it('identifies burstable performance instance types correctly', function () {
      const types = ['t2.small', 't3.nano', 't3a.medium', 't4g.large', 'm5.small', 'r5.4xlarge'];
      const service = this.awsInstanceTypeService;

      let supportedInstanceTypes = [];
      for (it of types) {
        const actualResult = service.isBurstingSupported(it);
        if (actualResult) {
          supportedInstanceTypes.push(it);
        }
      }

      expect(supportedInstanceTypes).toEqual(['t2.small', 't3.nano', 't3a.medium', 't4g.large']);
    });
  });

  describe('isInstanceTypeInCategory', function () {
    it('identifies instance types in category correctly', function () {
      const input = [
        { type: 'm5.large', cat: 'general' },
        { type: 't2.small', cat: 'general' },
        { type: 't2.medium', cat: 'general' },
        { type: 'r5.large', cat: 'memory' },
        { type: 'r5.xlarge', cat: 'memory' },
        { type: 't2.nano', cat: 'micro' },
        { type: 't2.micro', cat: 'micro' },
        { type: 't2.small', cat: 'micro' },
        { type: 't3.nano', cat: 'micro' },
        { type: 't3.micro', cat: 'micro' },
        { type: 't3.small', cat: 'micro' },
        { type: 'm3.small', cat: 'custom' },
        { type: 'a1.medium', cat: 'custom' },
      ];
      let service = this.awsInstanceTypeService;

      for (let test of input) {
        expect(service.isInstanceTypeInCategory(test.type, test.cat)).toBeTrue();
      }
    });

    it('returns false for instance types NOT in category', function () {
      const input = [
        { type: 't2.nano', cat: 'general' },
        { type: 't3.nano', cat: 'general' },
        { type: 't2.micro', cat: 'general' },
        { type: 't3.micro', cat: 'general' },
        { type: 't2.medium', cat: 'micro' },
        { type: 't3.medium', cat: 'micro' },
      ];
      let service = this.awsInstanceTypeService;

      for (let test of input) {
        expect(service.isInstanceTypeInCategory(test.type, test.cat)).toBeFalse();
      }
    });

    it('returns undefined for instance families NOT in category or invalid input', function () {
      const input = [
        { type: 'm5.large', cat: 'memory' },
        { type: 't2.small', cat: 'memory' },
        { type: 't3.something', cat: 'memory' },
        { type: 'r5.xlarge', cat: 'micro' },
        { type: 'm5.large', cat: 'invalid' },
        { type: 't2.invalid', cat: 'memory' },
        { type: 't3.invalid', cat: 'memory' },
        { type: 'invalid', cat: 'micro' },
        { type: 'invalid', cat: 'invalid' },
      ];
      let service = this.awsInstanceTypeService;

      for (let test of input) {
        expect(service.isInstanceTypeInCategory(test.type, test.cat)).toBeFalsy();
      }
    });
  });
});
