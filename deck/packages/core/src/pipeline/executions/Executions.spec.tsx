import { mock, noop } from 'angular';
import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import type { ReactWrapper } from 'enzyme';
import { mount, shallow } from 'enzyme';
import { set } from 'lodash';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { IExecutionsProps, IExecutionsState } from './Executions';
import { ExecutionsComponent } from './Executions';
import type { Application } from '../../application';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { ViewStateCache } from '../../cache';
import { INSIGHT_FILTER_STATE_MODEL } from '../../insight/insightFilterState.model';
import { OVERRIDE_REGISTRY } from '../../overrideRegistry';
import { REACT_MODULE } from '../../reactShims';
import * as State from '../../state';
import { Spinner } from '../../widgets/spinners/Spinner';
import { ManualExecutionModal } from '../manualExecution';

describe('<Executions/>', () => {
  let component: ReactWrapper<IExecutionsProps, IExecutionsState>;
  let application: Application;
  let router: UIRouterReact;
  let routerProps: any;
  const runtimeServices = {} as any;

  async function settleInitialization() {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    act(() => jasmine.clock().tick(50));
    component.update();
  }

  function initializeApplication(data?: any) {
    set(application, 'executions.activate', noop);
    set(application, 'pipelineConfigs.activate', noop);
    if (data && 'executions' in data) {
      application.executions.data = data.executions;
      application.executions.loaded = true;
    }
    if (data && 'pipelineConfigs' in data) {
      application.pipelineConfigs.data = data.pipelineConfigs;
      application.pipelineConfigs.loaded = true;
    }

    component = mount(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <UIRouterContext.Provider value={router}>
          <ExecutionsComponent {...routerProps} app={application} />
        </UIRouterContext.Provider>
      </DeckRuntimeContext.Provider>,
    );
  }

  beforeEach(() => {
    component = null;
    router = new UIRouterReact();
    routerProps = { router, stateParams: {}, stateService: { go: jasmine.createSpy('injectedGo') } };
  });
  beforeEach(mock.module(INSIGHT_FILTER_STATE_MODEL, REACT_MODULE, OVERRIDE_REGISTRY));
  beforeEach(() => jasmine.clock().install());
  beforeEach(
    mock.inject(() => {
      spyOn(ViewStateCache, 'createCache').and.returnValue({ get: noop, put: noop, touch: noop } as any);
      State.initialize();
      application = ApplicationModelBuilder.createApplicationForTests(
        'app',
        { key: 'executions', lazy: true, defaultData: [] },
        { key: 'pipelineConfigs', lazy: true, defaultData: [] },
        { key: 'runningExecutions', lazy: true, defaultData: [] },
      );
    }),
  );
  afterEach(async () => {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    component?.unmount();
    router.dispose();
    jasmine.clock().uninstall();
  });

  it('should not set loading flag to false until executions and pipeline configs have been loaded', async () => {
    initializeApplication();
    expect(component.find(Spinner).length).toBe(1);
    application.executions.dataUpdated();
    application.pipelineConfigs.dataUpdated();
    await settleInitialization();

    expect(component.find(Spinner).length).toBe(0);
  });

  it('clears the manual execution param through the injected state service', () => {
    const executionComponent = shallow(<ExecutionsComponent {...routerProps} app={application} />, {
      disableLifecycleMethods: true,
    });

    (executionComponent.instance() as any).clearManualExecutionParam();

    expect(routerProps.stateService.go).toHaveBeenCalledWith(
      '.',
      { startManualExecution: null },
      { inherit: true, location: 'replace' },
    );
  });

  it('starts a deep-linked manual execution from injected route params', async () => {
    const pipeline = { id: 'pipeline-id', name: 'Test Pipeline' };
    routerProps.stateParams = { startManualExecution: pipeline.id };
    const showModal = spyOn(ManualExecutionModal, 'show').and.returnValue(Promise.reject());
    initializeApplication({ executions: [], pipelineConfigs: [pipeline] });
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(showModal).toHaveBeenCalledWith(jasmine.objectContaining({ application, pipeline }), runtimeServices);
  });
});
