import type { Mock } from 'vitest';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
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
import { CollapsibleSectionStateCache, ViewStateCache } from '../../cache';
import { FilterCollapse } from '../../filterModel';
import { ManualExecutionModal } from '../manualExecution';
import { ApplicationForbidden } from '../../notfound/ApplicationForbidden';
import * as State from '../../state';
import { noop } from '../../utils';
import { Spinner } from '../../widgets/spinners/Spinner';

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
    act(() => vi.advanceTimersByTime(50));
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
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    routerProps = { router, stateParams: {}, stateService: { go: vi.fn() } };
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
    vi.spyOn(CollapsibleSectionStateCache, 'isExpanded').mockReturnValue(false);
    vi.spyOn(CollapsibleSectionStateCache, 'setExpanded').mockReturnValue(undefined);
  });
  beforeEach(() => vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] }));
  beforeEach(() => {
    vi.spyOn(ViewStateCache, 'createCache').mockReturnValue({ get: noop, put: noop, touch: noop } as any);
    State.initialize();
    State.ExecutionState.filterModel.asFilterModel.sortFilter.filter = 'existing filter';
    application = ApplicationModelBuilder.createApplicationForTests(
      'app',
      { key: 'executions', lazy: true, defaultData: [] },
      { key: 'pipelineConfigs', lazy: true, defaultData: [] },
      { key: 'runningExecutions', lazy: true, defaultData: [] },
    );
  });
  afterEach(async () => {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    component?.unmount();
    router.dispose();
    vi.useRealTimers();
  });

  it('should not set loading flag to false until executions and pipeline configs have been loaded', async () => {
    initializeApplication();
    expect(component.find(Spinner).length).toBe(1);
    application.executions.loaded = true;
    application.pipelineConfigs.loaded = true;
    application.executions.dataUpdated();
    application.pipelineConfigs.dataUpdated();
    await settleInitialization();

    expect(component.find(Spinner).length).toBe(0);
  });

  it('controls filter expansion from the cache and persists committed toggles', async () => {
    (CollapsibleSectionStateCache.isSet as Mock).mockReturnValue(true);
    (CollapsibleSectionStateCache.isExpanded as Mock).mockReturnValue(false);
    initializeApplication({ executions: [], pipelineConfigs: [{ id: 'pipeline-id' }] });
    await settleInitialization();

    let filterCollapse = component.find(FilterCollapse);
    expect(CollapsibleSectionStateCache.isSet).toHaveBeenCalledWith('insightFilters');
    expect(CollapsibleSectionStateCache.isExpanded).toHaveBeenCalledWith('insightFilters');
    expect(filterCollapse.prop('filtersExpanded')).toBe(false);
    expect(CollapsibleSectionStateCache.setExpanded).not.toHaveBeenCalled();

    const onToggle = filterCollapse.prop('onToggle') as () => void;
    act(() => {
      onToggle();
      onToggle();
    });
    component.update();

    filterCollapse = component.find(FilterCollapse);
    expect(filterCollapse.prop('filtersExpanded')).toBe(false);
    expect(CollapsibleSectionStateCache.setExpanded).not.toHaveBeenCalled();

    act(() => onToggle());
    component.update();

    filterCollapse = component.find(FilterCollapse);
    expect(filterCollapse.prop('filtersExpanded')).toBe(true);
    expect((CollapsibleSectionStateCache.setExpanded as Mock).mock.calls).toEqual([['insightFilters', true]]);
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
    const showModal = vi.spyOn(ManualExecutionModal, 'show').mockReturnValue(Promise.reject());
    initializeApplication({ executions: [], pipelineConfigs: [pipeline] });
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(showModal).toHaveBeenCalledWith(expect.objectContaining({ application, pipeline }), runtimeServices);
  });
});
