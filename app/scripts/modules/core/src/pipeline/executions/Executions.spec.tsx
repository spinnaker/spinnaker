import * as React from 'react';
import { ReactWrapper, mount } from 'enzyme';
import { set } from 'lodash';
import { IScope, ITimeoutService, mock, noop } from 'angular';

import { Application } from 'core/application';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { INSIGHT_FILTER_STATE_MODEL } from 'core/insight/insightFilterState.model';
import { REACT_MODULE, ReactInjector } from 'core/reactShims';
import { ScrollToService } from 'core/utils';
import { IExecutionsProps, IExecutionsState, Executions } from './Executions';

describe('<Executions/>', () => {
  let component: ReactWrapper<IExecutionsProps, IExecutionsState>;
  let application: Application;
  let scope: IScope;
  let $timeout: ITimeoutService;

  function initializeApplication(data?: any) {
    set(application, 'executions.activate', noop);
    set(application, 'pipelineConfigs.activate', noop);
    if (data && data.executions) {
      application.executions.data = data.executions;
      application.executions.loaded = true;
    }
    if (data && data.pipelineConfigs) {
      application.pipelineConfigs.data = data.pipelineConfigs;
      application.pipelineConfigs.loaded = true;
    }

    component = mount(<Executions app={application} />);
  }

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER, INSIGHT_FILTER_STATE_MODEL, REACT_MODULE));
  beforeEach(
    mock.inject((_$timeout_: ITimeoutService, $rootScope: IScope, applicationModelBuilder: ApplicationModelBuilder) => {
      scope = $rootScope.$new();
      $timeout = _$timeout_;
      application = applicationModelBuilder.createApplicationForTests(
        'app',
        { key: 'executions', lazy: true },
        { key: 'pipelineConfigs', lazy: true },
      );
    }),
  );

  it('should not set loading flag to false until executions and pipeline configs have been loaded', function() {
    initializeApplication();

    expect(component.state().loading).toBe(true);
    application.executions.dataUpdated();
    application.pipelineConfigs.dataUpdated();
    scope.$digest();
    $timeout.flush();
    expect(component.state().loading).toBe(false);
  });

  describe('auto-scrolling behavior', function() {
    beforeEach(function() {
      spyOn(ScrollToService, 'scrollTo');
    });

    it('should scroll execution into view on initialization if an execution is present in state params', function() {
      ReactInjector.$stateParams.executionId = 'a';

      initializeApplication({ pipelineConfigs: [], executions: [] });
      scope.$digest();

      expect((ScrollToService.scrollTo as any).calls.count()).toBe(1);
    });

    it('should NOT scroll execution into view on initialization if none present in state params', function() {
      initializeApplication();
      scope.$digest();

      expect((ScrollToService.scrollTo as any).calls.count()).toBe(0);
    });

    // TODO: Figure out how to test transitions
    // it('should scroll execution into view on state change success if no execution id in state params', function () {
    //   initializeApplication();
    //   scope.$digest();

    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(0);

    //   scope.$broadcast('$stateChangeSuccess', {name: 'executions.execution'}, {executionId: 'a'}, {name: 'executions'}, {});
    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(1);
    // });

    // it('should scroll execution into view on state change success if execution id changes', function () {
    //   initializeApplication();
    //   scope.$digest();

    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(0);

    //   scope.$broadcast('$stateChangeSuccess', {name: 'executions.execution'}, {executionId: 'a'}, {name: 'executions.execution'}, {executionId: 'b'});
    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(1);
    // });

    // it('should scroll into view if no params change, because the user clicked on a link somewhere else in the page', function () {
    //   const params = {executionId: 'a', step: 'b', stage: 'c', details: 'd'};

    //   initializeApplication();
    //   scope.$digest();

    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(0);

    //   scope.$broadcast('$stateChangeSuccess', {name: 'executions.execution'}, params, {name: 'executions.execution'}, params);
    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(1);
    // });

    // it('should NOT scroll into view if step changes', function () {
    //   const toParams = {executionId: 'a', step: 'b', stage: 'c', details: 'd'},
    //       fromParams = {executionId: 'a', step: 'c', stage: 'c', details: 'd'};

    //   initializeApplication();
    //   scope.$digest();
    //   scope.$broadcast('$stateChangeSuccess', {name: 'executions'}, toParams, {name: 'executions'}, fromParams);
    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(0);
    // });

    // it('should NOT scroll into view if stage changes', function () {
    //   const toParams = {executionId: 'a', step: 'b', stage: 'c', details: 'd'},
    //       fromParams = {executionId: 'a', step: 'b', stage: 'e', details: 'd'};

    //   initializeApplication();
    //   scope.$digest();
    //   scope.$broadcast('$stateChangeSuccess', {name: 'executions'}, toParams, {name: 'executions'}, fromParams);
    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(0);
    // });

    // it('should NOT scroll into view if detail changes', function () {
    //   const toParams = {executionId: 'a', step: 'b', stage: 'c', details: 'd'},
    //       fromParams = {executionId: 'a', step: 'b', stage: 'c', details: 'e'};

    //   initializeApplication();
    //   scope.$digest();
    //   scope.$broadcast('$stateChangeSuccess', {name: 'executions'}, toParams, {name: 'executions'}, fromParams);
    //   expect((ReactInjector.scrollToService.scrollTo as any).calls.count()).toBe(0);
    // });
  });
});
