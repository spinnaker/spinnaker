import * as _ from 'lodash';
import {ITimeoutService, mock} from 'angular';

import {Application} from 'core/application/application.model';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {EXECUTION_FILTER_MODEL, ExecutionFilterModel} from 'core/delivery/filter/executionFilter.model';
import {EXECUTION_FILTER_SERVICE, ExecutionFilterService} from './executionFilter.service';
import {IExecution} from 'core/domain/IExecution';

describe('Service: executionFilterService', function () {

  let modelBuilder: ApplicationModelBuilder;
  let service: ExecutionFilterService;
  let model: ExecutionFilterModel;
  let $timeout: ITimeoutService;

  beforeEach(function() {
    spyOn(_, 'debounce').and.callFake((fn: (app: any) => any) => (app: any) => $timeout(fn(app)));
    mock.module(
      APPLICATION_MODEL_BUILDER,
      EXECUTION_FILTER_SERVICE,
      EXECUTION_FILTER_MODEL
    );
    mock.inject(
      function (applicationModelBuilder: ApplicationModelBuilder, executionFilterService: ExecutionFilterService, executionFilterModel: ExecutionFilterModel, _$timeout_: ITimeoutService) {
        modelBuilder = applicationModelBuilder;
        service = executionFilterService;
        model = executionFilterModel;
        $timeout = _$timeout_;
        executionFilterModel.asFilterModel.groups = [];
        spyOn(model.asFilterModel, 'applyParamsToUrl').and.callFake(() => {});
      }
    );
  });

  describe('Sorting', () => {
    it('sorts pipeline groups by index, always putting strategies at the end, followed by ad-hoc pipelines', () => {
      const firstGroup = { config: { index: 1} };
      const secondGroup = { config: { index: 2} };
      const strategy = { config: { index: 0, strategy: true } };
      const adHocA = { heading: 'a' };
      const adHocB = { heading: 'b' };
      const groups = [strategy, adHocB, adHocA, secondGroup, firstGroup];
      const sorted = groups.sort((a: any, b: any) => service.executionGroupSorter(a, b));

      expect(sorted).toEqual([firstGroup, secondGroup, strategy, adHocA, adHocB]);
    });
  });

  describe('Updating execution groups', function () {

    it('limits executions per pipeline', function () {
      const application: Application = modelBuilder.createApplication({key: 'executions', lazy: true}, {key: 'pipelineConfigs', lazy: true});
      application.getDataSource('executions').data = [
        { pipelineConfigId: '1', name: 'pipeline 1', endTime: 1, stages: [] },
        { pipelineConfigId: '1', name: 'pipeline 1', endTime: 2, stages: [] },
        { pipelineConfigId: '1', name: 'pipeline 1', endTime: 3, stages: [] },
        { pipelineConfigId: '2', name: 'pipeline 2', endTime: 1, stages: [] },
      ];
      application.getDataSource('pipelineConfigs').data = [
        { name: 'pipeline 1', pipelineConfigId: '1' },
        { name: 'pipeline 2', pipelineConfigId: '2' },
      ];

      model.asFilterModel.sortFilter.count = 2;
      model.asFilterModel.sortFilter.groupBy = 'none';

      service.updateExecutionGroups(application);
      $timeout.flush();

      expect(model.asFilterModel.groups.length).toBe(1);
      expect(model.asFilterModel.groups[0].executions.length).toBe(3);
      expect(model.asFilterModel.groups[0].executions.filter((ex: IExecution) => ex.pipelineConfigId === '1').length).toBe(2);
      expect(model.asFilterModel.groups[0].executions.filter((ex: IExecution) => ex.pipelineConfigId === '2').length).toBe(1);

      model.asFilterModel.sortFilter.groupBy = 'name';
      service.updateExecutionGroups(application);
      $timeout.flush();

      expect(model.asFilterModel.groups.length).toBe(2);
      expect(model.asFilterModel.groups[0].executions.length).toBe(2);
      expect(model.asFilterModel.groups[1].executions.length).toBe(1);

    });

  });
});
