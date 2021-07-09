import { ExecutionFilterService } from './executionFilter.service';
import { ExecutionFilterModel } from './ExecutionFilterModel';
import { ExecutionState } from '../../state';

describe('ExecutionFilterService', function () {
  let model: ExecutionFilterModel;

  beforeEach(function () {
    model = ExecutionState.filterModel;
    model.asFilterModel.groups = [];
    spyOn(model.asFilterModel, 'applyParamsToUrl').and.callFake(() => {});
  });

  describe('Sorting', () => {
    it('sorts pipeline groups by index, always putting strategies at the end, followed by ad-hoc pipelines', () => {
      const firstGroup = { config: { index: 1 } };
      const secondGroup = { config: { index: 2 } };
      const strategy = { config: { index: 0, strategy: true } };
      const adHocA = { heading: 'a' };
      const adHocB = { heading: 'b' };
      const groups = [strategy, adHocB, adHocA, secondGroup, firstGroup];
      const sorted = groups.sort((a: any, b: any) => ExecutionFilterService.executionGroupSorter(a, b));

      expect(sorted).toEqual([firstGroup, secondGroup, strategy, adHocA, adHocB]);
    });
  });
});
