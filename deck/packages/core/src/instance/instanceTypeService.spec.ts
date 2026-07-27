import type { IInstanceTypeCategory } from './instanceType.service';
import { InstanceTypeService } from './instanceType.service';

describe('Service: instanceTypeService', function () {
  let instanceTypeService: InstanceTypeService;

  const m3Category: IInstanceTypeCategory = {
    type: 'general',
    families: [
      {
        type: 'm3',
        instanceTypes: [{ name: 'm3.medium' }, { name: 'm3.large' }, { name: 'm3.xlarge' }, { name: 'm3.2xlarge' }],
      },
    ],
  };

  const r3Category: IInstanceTypeCategory = {
    type: 'memory',
    families: [
      {
        type: 'r3',
        instanceTypes: [{ name: 'r3.large' }, { name: 'r3.xlarge' }, { name: 'r3.2xlarge' }, { name: 'r3.4xlarge' }],
      },
    ],
  };

  const t2Category: IInstanceTypeCategory = {
    type: 'micro',
    families: [
      {
        type: 't2',
        instanceTypes: [{ name: 't2.small' }, { name: 't2.medium' }],
      },
    ],
  };

  const gceCustomInstanceCategory: IInstanceTypeCategory = {
    type: 'buildCustom',
    families: [
      {
        type: 'buildCustom',
        instanceTypes: [{ name: 'buildCustom', nameRegex: /custom-\d{1,2}-\d{4,6}/ }],
      },
    ],
  };

  const categories: IInstanceTypeCategory[] = [m3Category, r3Category, t2Category, gceCustomInstanceCategory];

  beforeEach(() => {
    instanceTypeService = new InstanceTypeService({
      getDelegate: () => ({ getCategories: () => Promise.resolve(categories) }),
    } as any);
  });

  describe('find profile name for instance type', function () {
    m3Category.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "general" if the ' + instanceType.name + ' is in the "general" category', async function () {
        const result = await instanceTypeService.getCategoryForInstanceType('aws', instanceType.name);
        expect(result).toBe('general');
      });
    });

    r3Category.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "memory" if the ' + instanceType.name + ' is in the "memory" category', async function () {
        const result = await instanceTypeService.getCategoryForInstanceType('aws', instanceType.name);
        expect(result).toBe('memory');
      });
    });

    t2Category.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "micro" if the ' + instanceType.name + ' is in the "micro" category', async function () {
        const result = await instanceTypeService.getCategoryForInstanceType('aws', instanceType.name);
        expect(result).toBe('micro');
      });
    });

    const customTypes = ['c1.large', 'c3.large', 'c4.large', 'm2.large'];
    customTypes.forEach(function (instanceType) {
      it('should return "custom" if the ' + instanceType + ' is not in a category', async function () {
        const result = await instanceTypeService.getCategoryForInstanceType('aws', instanceType);
        expect(result).toBe('custom');
      });
    });

    const gceBuildCustomTypes = ['custom-1-2816', 'custom-6-9984'];
    gceBuildCustomTypes.forEach(function (instanceType) {
      it('should return "buildCustom" for ' + instanceType, async function () {
        const result = await instanceTypeService.getCategoryForInstanceType('gce', instanceType);
        expect(result).toBe('buildCustom');
      });
    });
  });
});
