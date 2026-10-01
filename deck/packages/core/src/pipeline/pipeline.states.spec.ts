import type { Mock } from 'vitest';
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

  function createRouter(getExecution?: Mock): UIRouterReact {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    if (getExecution) {
      vi.spyOn(runtime.services.executionService, 'getExecution').mockImplementation(getExecution);
    }
    router.disposable({ dispose: runtime.dispose });
    configureRouter(router, runtime.services, runtime.routingState);
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

    function getRedirectTo(getExecution?: Mock) {
      const router = createRouter(getExecution);
      const executionLookup = router.stateRegistry.get('home.executionLookup');
      return executionLookup.redirectTo;
    }

    function createTransition(transitionParams: Record<string, string | undefined>, target: Mock): Transition {
      return ({
        params: () => transitionParams,
        router: { stateService: { target } },
      } as unknown) as Transition;
    }

    it('resolves an execution permalink without a transition injector and preserves all target parameters', async () => {
      const execution = { application: 'resolved-application', id: params.executionId };
      const getExecution = vi.fn().mockResolvedValue(execution);
      const targetResult = { redirected: true };
      const target = vi.fn().mockReturnValue(targetResult);

      const result = await getRedirectTo(getExecution)(createTransition(params, target));

      expect(getExecution).toHaveBeenCalledExactlyOnceWith(params.executionId);
      expect(target).toHaveBeenCalledExactlyOnceWith(
        'home.applications.application.pipelines.executionDetails.execution',
        {
          application: execution.application,
          executionId: execution.id,
          refId: params.refId,
          stage: params.stage,
          subStage: params.subStage,
          step: params.step,
          details: params.details,
          stageId: params.stageId,
        },
      );
      expect(result).toBe(targetResult);
    });

    it('resolves an execution permalink through a real direct transition', async () => {
      const execution = { application: 'resolved-application', id: params.executionId };
      const getExecution = vi.fn().mockResolvedValue(execution);
      vi.spyOn(ApplicationReader, 'getApplication').mockResolvedValue({
        name: execution.application,
        dataSources: [],
      } as any);
      const router = createRouter(getExecution);

      await router.stateService.go('home.executionLookup', params, { location: false });

      expect(router.stateService.current.name).toBe(
        'home.applications.application.pipelines.executionDetails.execution',
      );
      expect(router.globals.params).toEqual(
        expect.objectContaining({ application: execution.application, executionId: execution.id }),
      );
    });

    it('returns undefined without looking up an execution when the execution ID is missing', () => {
      const getExecution = vi.fn();
      const target = vi.fn();

      const result = getRedirectTo(getExecution)(createTransition({ ...params, executionId: undefined }, target));

      expect(result).toBeUndefined();
      expect(getExecution).not.toHaveBeenCalled();
      expect(target).not.toHaveBeenCalled();
    });

    it('returns undefined when the execution lookup is rejected', async () => {
      const getExecution = vi.fn().mockRejectedValue(new Error('not found'));
      const target = vi.fn();

      const result = await getRedirectTo(getExecution)(createTransition(params, target));

      expect(result).toBeUndefined();
      expect(target).not.toHaveBeenCalled();
    });
  });
});
