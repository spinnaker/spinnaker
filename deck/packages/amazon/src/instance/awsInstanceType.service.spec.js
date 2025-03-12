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
        {
          account: 'test',
          region: 'us-west-2',
          name: 'm1.small',
          defaultVCpus: 1,
          memoryInGiB: 1,
          supportedArchitectures: ['i386', 'x86_64'],
          supportedVirtualizationTypes: ['hvm', 'paravirtual'],
        },
        {
          account: 'test',
          region: 'us-west-2',
          name: 'm2.xlarge',
          defaultVCpus: 2,
          memoryInGiB: 17,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm', 'paravirtual'],
        },
        {
          account: 'test',
          region: 'eu-west-1',
          name: 'hs1.8xlarge',
          defaultVCpus: 32,
          memoryInGiB: 256,
          supportedArchitectures: ['i386', 'x86_64'],
          supportedVirtualizationTypes: ['hvm', 'paravirtual'],
        },
        {
          account: 'test',
          region: 'eu-west-1',
          name: 'm2.xlarge',
          defaultVCpus: 2,
          memoryInGiB: 17,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm', 'paravirtual'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 'm4.2xlarge',
          defaultVCpus: 8,
          memoryInGiB: 32,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 't2.nano',
          defaultVCpus: 1,
          memoryInGiB: 0,
          supportedArchitectures: ['i386', 'x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 't2.micro',
          defaultVCpus: 1,
          memoryInGiB: 1,
          supportedArchitectures: ['i386', 'x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 'm4.xlarge',
          defaultVCpus: 4,
          memoryInGiB: 16,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 'm4.4xlarge',
          defaultVCpus: 16,
          memoryInGiB: 64,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 't2.medium',
          defaultVCpus: 2,
          memoryInGiB: 4,
          supportedArchitectures: ['i386', 'x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 'm4.large',
          defaultVCpus: 2,
          memoryInGiB: 8,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 'm4.16xlarge',
          defaultVCpus: 64,
          memoryInGiB: 256,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 'm4.10xlarge',
          defaultVCpus: 40,
          memoryInGiB: 160,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
        {
          account: 'test',
          region: 'us-east-1',
          name: 't2.loltiny',
          defaultVCpus: 1,
          memoryInGiB: 0,
          supportedArchitectures: ['i386', 'x86_64'],
          supportedVirtualizationTypes: ['hvm'],
        },
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
      expect(results).toEqual([
        {
          account: 'test',
          region: 'us-west-2',
          name: 'm1.small',
          defaultVCpus: 1,
          memoryInGiB: 1,
          supportedArchitectures: ['i386', 'x86_64'],
          supportedVirtualizationTypes: ['hvm', 'paravirtual'],
          key: 'us-west-2:test:m1.small',
        },
        {
          account: 'test',
          region: 'us-west-2',
          name: 'm2.xlarge',
          defaultVCpus: 2,
          memoryInGiB: 17,
          supportedArchitectures: ['x86_64'],
          supportedVirtualizationTypes: ['hvm', 'paravirtual'],
          key: 'us-west-2:test:m2.xlarge',
        },
      ]);
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
      expect(map(results, 'name')).toEqual(['m2.xlarge']);
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
      expect(map(results, 'name')).toEqual([
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

  describe('filterInstanceTypes', function () {
    const instanceTypes = [
      {
        account: 'test',
        region: 'us-east-1',
        name: 'test.type.incomplete',
      },
      {
        account: 'test',
        region: 'us-west-2',
        name: 'm1.small',
        defaultVCpus: 1,
        memoryInGiB: 1,
        supportedArchitectures: ['i386', 'x86_64'],
        supportedVirtualizationTypes: ['hvm', 'paravirtual'],
      },
      {
        account: 'test',
        region: 'eu-west-1',
        name: 'hs1.8xlarge',
        defaultVCpus: 32,
        memoryInGiB: 256,
        supportedArchitectures: ['i386', 'x86_64'],
        supportedVirtualizationTypes: ['hvm', 'paravirtual'],
      },
      {
        account: 'test',
        region: 'eu-west-1',
        name: 'm2.xlarge',
        defaultVCpus: 2,
        memoryInGiB: 17,
        supportedArchitectures: ['x86_64'],
        supportedVirtualizationTypes: ['hvm', 'paravirtual'],
      },
      {
        account: 'test',
        region: 'us-east-1',
        name: 'm4.2xlarge',
        defaultVCpus: 8,
        memoryInGiB: 32,
        supportedArchitectures: ['x86_64'],
        supportedVirtualizationTypes: ['hvm'],
      },
      {
        account: 'test',
        region: 'us-east-1',
        name: 't2.nano',
        defaultVCpus: 1,
        memoryInGiB: 0,
        supportedArchitectures: ['i386', 'x86_64'],
        supportedVirtualizationTypes: ['hvm'],
      },
    ];

    it('filters instance types by VPC, virtualization type and architecture, only if the information exists', function () {
      const service = this.awsInstanceTypeService;
      expect(map(service.filterInstanceTypes(instanceTypes, 'hvm', true, 'x86_64'), 'name')).toEqual([
        'test.type.incomplete',
        'm1.small',
        'hs1.8xlarge',
        'm2.xlarge',
        'm4.2xlarge',
        't2.nano',
      ]);
      expect(map(service.filterInstanceTypes(instanceTypes, 'hvm', false, 'x86_64'), 'name')).toEqual([
        'm1.small',
        'hs1.8xlarge',
        'm2.xlarge',
      ]);
      expect(map(service.filterInstanceTypes(instanceTypes, 'hvm', true, 'i386'), 'name')).toEqual([
        'test.type.incomplete',
        'm1.small',
        'hs1.8xlarge',
        't2.nano',
      ]);
      expect(map(service.filterInstanceTypes(instanceTypes, 'paravirtual', true, 'i386'), 'name')).toEqual([
        'test.type.incomplete',
        'm1.small',
        'hs1.8xlarge',
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
