'use strict';
import { map } from 'lodash';
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
      expect(map(results['us-west-2'], 'name').sort()).toEqual(['m1.small', 'm2.xlarge']);
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

  describe('isBurstingSupportedForAllTypes', function () {
    const testInputs = [
      { types: ['t2.small', 't3.nano', 't3a.medium', 't4g.large'], result: true },
      { types: ['m5.large'], result: false },
      { types: ['t2.small', 't3.nano', 't3a.medium', 't4g.large', 'm5.small'], result: false },
      { types: ['t2.small', 'r5.large'], result: false },
      { types: ['invalid'], result: false },
      { types: [], result: false },
      { types: null, result: false },
    ];

    testInputs.forEach((testInput) => {
      it(`identifies if bursting is supported for all instance types passed correctly for ${testInput.types}`, function () {
        const actualResult = this.awsInstanceTypeService.isBurstingSupportedForAllTypes(testInput.types);
        expect(actualResult).toEqual(testInput.result);
      });
    });
  });

  describe('getInstanceTypesInCategory', function () {
    const testInputs = [
      { types: ['m5.large'], cat: 'general', result: ['m5.large'] },
      { types: ['t2.small', 't2.medium'], cat: 'general', result: ['t2.small', 't2.medium'] },
      { types: ['r5.large', 'r5.xlarge'], cat: 'memory', result: ['r5.large', 'r5.xlarge'] },
      { types: ['t2.nano', 't2.micro', 't2.small'], cat: 'micro', result: ['t2.nano', 't2.micro', 't2.small'] },
      { types: ['t3.nano', 't3.micro', 't3.small'], cat: 'micro', result: ['t3.nano', 't3.micro', 't3.small'] },
      { types: ['m3.small', 'a1.medium'], cat: 'custom', result: ['m3.small', 'a1.medium'] },
      { types: ['t3.nano', 'm3.large', 'r5.large'], cat: 'micro', result: ['t3.nano'] },
      { types: ['m5.small', 'r5.large'], cat: 'memory', result: ['r5.large'] },
      { types: ['invalid'], cat: 'micro', result: [] },
      { types: ['invalid'], cat: 'invalid', result: [] },
      { types: ['t2.invalid'], cat: 'micro', result: [] },
      { types: ['m5.small', 'a1.medium'], cat: 'general', result: [] },
    ];

    testInputs.forEach((testInput) => {
      it(`identifies instance types in category correctly for ${testInput.types}`, function () {
        const actual = this.awsInstanceTypeService.getInstanceTypesInCategory(testInput.types, testInput.cat);
        expect(actual).toEqual(testInput.result);
      });
    });
  });
});
