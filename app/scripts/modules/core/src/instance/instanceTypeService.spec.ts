import { mock } from 'angular';

import { INSTANCE_TYPE_SERVICE, InstanceTypeService, IInstanceTypeCategory } from './instanceType.service';

describe('Service: instanceTypeService', function () {
  let instanceTypeService: InstanceTypeService, $q: ng.IQService, $scope: ng.IScope;

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

  beforeEach(mock.module(INSTANCE_TYPE_SERVICE));

  beforeEach(
    mock.inject(function (
      _instanceTypeService_: InstanceTypeService,
      _$q_: ng.IQService,
      $rootScope: ng.IRootScopeService,
      providerServiceDelegate: any,
    ) {
      instanceTypeService = _instanceTypeService_;
      $q = _$q_;
      $scope = $rootScope.$new();

      spyOn(providerServiceDelegate, 'getDelegate').and.returnValue({
        getCategories: () => {
          return $q.when(categories);
        },
      });
    }),
  );

  describe('find profile name for instance type', function () {
    beforeEach(function () {
      spyOn(instanceTypeService, 'getCategories').and.returnValue($q.when(categories));
    });

    m3Category.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "general" if the ' + instanceType.name + ' is in the "general" category', function () {
        let result: string = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType.name).then(function (category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('general');
      });
    });

    r3Category.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "memory" if the ' + instanceType.name + ' is in the "memory" category', function () {
        let result: string = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType.name).then(function (category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('memory');
      });
    });

    t2Category.families[0].instanceTypes.forEach(function (instanceType) {
      it('should return "micro" if the ' + instanceType.name + ' is in the "micro" category', function () {
        let result: string = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType.name).then(function (category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('micro');
      });
    });

    const customTypes = ['c1.large', 'c3.large', 'c4.large', 'm2.large'];
    customTypes.forEach(function (instanceType) {
      it('should return "custom" if the ' + instanceType + ' is not in a category', function () {
        let result: string = null;
        instanceTypeService.getCategoryForInstanceType('aws', instanceType).then(function (category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('custom');
      });
    });

    const gceBuildCustomTypes = ['custom-1-2816', 'custom-6-9984'];
    gceBuildCustomTypes.forEach(function (instanceType) {
      it('should return "buildCustom" for ' + instanceType, function () {
        let result: string = null;
        instanceTypeService.getCategoryForInstanceType('gce', instanceType).then(function (category) {
          result = category;
        });
        $scope.$digest();
        expect(result).toBe('buildCustom');
      });
    });
  });
});
