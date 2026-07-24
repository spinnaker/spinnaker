import type { Transition } from '@uirouter/core';
import { UIRouterReact, UIView } from '@uirouter/react';
import { shallow } from 'enzyme';
import React from 'react';

import { ApplicationReader } from '../application/service/ApplicationReader';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { setDirectRouter } from '../navigation/directRouter';
import { configureRouter } from '../navigation/router';
import { SpinErrorBoundary } from '../presentation';

import './pipeline.states';

describe('pipeline states', () => {
  const routers: UIRouterReact[] = [];

  function createRouter(getExecution?: jasmine.Spy): UIRouterReact {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    if (getExecution) {
      spyOn(runtime.services.executionService, 'getExecution').and.callFake(getExecution);
    }
    router.disposable({ dispose: runtime.dispose });
    configureRouter(router, runtime.services);
    routers.push(router);
    return router;
  }

  afterEach(() => {
    routers.splice(0).forEach((router) => router.dispose());
    setDirectRouter(null);
  });

  it('registers the pipelines parent view with a direct React component', () => {
    const router = createRouter();
    const pipelinesState = router.stateRegistry.get('home.applications.application.pipelines');

    expect(pipelinesState.views.insight.component).toBeDefined();
    expect(pipelinesState.views.insight.template).toBeUndefined();
  });

  it('preserves the application secondary panel wrapper for direct React pipeline routes', () => {
    const router = createRouter();
    const pipelinesState = router.stateRegistry.get('home.applications.application.pipelines');
    const RoutedPipelineInsight = pipelinesState.views.insight.component;
    const errorBoundary = shallow(React.createElement(RoutedPipelineInsight, { className: 'secondary-panel' }));
    const PipelineInsightView = errorBoundary.find(SpinErrorBoundary).prop('children').type;

    const wrapper = shallow(React.createElement(PipelineInsightView, { className: 'secondary-panel' }));

    expect(wrapper.hasClass('secondary-panel')).toBe(true);
    expect(wrapper.find(UIView).prop('name')).toBe('pipelines');
    expect(wrapper.find(UIView).prop('className')).toBe('flex-fill');
  });

  describe('executionLookup', () => {
    const params = {
      application: 'ignored-application',
      executionId: 'execution-id',
      refId: 'ref-id',
      stage: '2',
      subStage: '3',
      step: '4',
      details: 'details',
      stageId: 'stage-id',
    };

    function getRedirectTo(getExecution?: jasmine.Spy) {
      const router = createRouter(getExecution);
      const executionLookup = router.stateRegistry.get('home.executionLookup');
      return executionLookup.redirectTo;
    }

    function createTransition(transitionParams: Record<string, string | undefined>, target: jasmine.Spy): Transition {
      return ({
        params: () => transitionParams,
        router: { stateService: { target } },
      } as unknown) as Transition;
    }

    it('resolves an execution permalink without a transition injector and preserves all target parameters', async () => {
      const execution = { application: 'resolved-application', id: params.executionId };
      const getExecution = jasmine.createSpy('getExecution').and.resolveTo(execution);
      const targetResult = { redirected: true };
      const target = jasmine.createSpy('target').and.returnValue(targetResult);

      const result = await getRedirectTo(getExecution)(createTransition(params, target));

      expect(getExecution).toHaveBeenCalledOnceWith(params.executionId);
      expect(target).toHaveBeenCalledOnceWith('home.applications.application.pipelines.executionDetails.execution', {
        application: execution.application,
        executionId: execution.id,
        refId: params.refId,
        stage: params.stage,
        subStage: params.subStage,
        step: params.step,
        details: params.details,
        stageId: params.stageId,
      });
      expect(result).toBe(targetResult);
    });

    it('resolves an execution permalink through a real direct transition', async () => {
      const execution = { application: 'resolved-application', id: params.executionId };
      const getExecution = jasmine.createSpy('getExecution').and.resolveTo(execution);
      spyOn(ApplicationReader, 'getApplication').and.resolveTo({
        name: execution.application,
        dataSources: [],
      } as any);
      const router = createRouter(getExecution);

      await router.stateService.go('home.executionLookup', params, { location: false });

      expect(router.stateService.current.name).toBe(
        'home.applications.application.pipelines.executionDetails.execution',
      );
      expect(router.globals.params).toEqual(
        jasmine.objectContaining({ application: execution.application, executionId: execution.id }),
      );
    });

    it('returns undefined without looking up an execution when the execution ID is missing', () => {
      const getExecution = jasmine.createSpy('getExecution');
      const target = jasmine.createSpy('target');

      const result = getRedirectTo(getExecution)(createTransition({ ...params, executionId: undefined }, target));

      expect(result).toBeUndefined();
      expect(getExecution).not.toHaveBeenCalled();
      expect(target).not.toHaveBeenCalled();
    });

    it('returns undefined when the execution lookup is rejected', async () => {
      const getExecution = jasmine.createSpy('getExecution').and.rejectWith(new Error('not found'));
      const target = jasmine.createSpy('target');

      const result = await getRedirectTo(getExecution)(createTransition(params, target));

      expect(result).toBeUndefined();
      expect(target).not.toHaveBeenCalled();
    });
  });
});
