import { act, fireEvent, render, screen } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { set } from 'lodash';
import React from 'react';
import type { Mock } from 'vitest';

import { ExecutionsComponent } from './Executions';
import type { Application } from '../../application';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { CollapsibleSectionStateCache, DeckCacheFactory, ViewStateCache } from '../../cache';
import type { ICache } from '../../cache';
import type { IPipeline } from '../../domain';
import { ManualExecutionModal } from '../manualExecution';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import * as State from '../../state';
import { noop } from '../../utils';

describe('<Executions/>', () => {
  let component: RenderResult;
  let application: Application;
  let router: UIRouterReact;
  let routerProps: IRouterInjectedProps;
  let viewStateCache: ICache;
  const runtimeServices = {};
  const runtime = ({ services: runtimeServices } as unknown) as React.ContextType<typeof DeckRuntimeContext>;

  async function settleInitialization() {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    act(() => {
      vi.advanceTimersByTime(50);
    });
  }

  function initializeApplication(data?: { executions?: unknown[]; pipelineConfigs?: IPipeline[] }) {
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

    component = render(
      <DeckRuntimeContext.Provider value={runtime}>
        <UIRouterContext.Provider value={router}>
          <ExecutionsComponent {...routerProps} app={application} />
        </UIRouterContext.Provider>
      </DeckRuntimeContext.Provider>,
    );
  }

  beforeEach(() => {
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    routerProps = {
      router,
      stateParams: {},
      stateService: ({ go: vi.fn() } as unknown) as IRouterInjectedProps['stateService'],
    };
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
    vi.spyOn(CollapsibleSectionStateCache, 'isExpanded').mockReturnValue(false);
    vi.spyOn(CollapsibleSectionStateCache, 'setExpanded').mockReturnValue(undefined);
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    });
    viewStateCache = DeckCacheFactory.createCache('test', 'executions-view-state', { storageMode: 'memory' });
    vi.spyOn(ViewStateCache, 'createCache').mockReturnValue(viewStateCache);
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
    viewStateCache.destroy();
    vi.useRealTimers();
  });

  it('should not set loading flag to false until executions and pipeline configs have been loaded', async () => {
    initializeApplication();
    expect(component.container.querySelector('.spinner-container')).toBeInTheDocument();

    act(() => {
      application.executions.loaded = true;
      application.pipelineConfigs.loaded = true;
      application.executions.dataUpdated();
      application.pipelineConfigs.dataUpdated();
    });
    await settleInitialization();

    expect(component.container.querySelector('.spinner-container')).not.toBeInTheDocument();
  });

  it('controls filter expansion from the cache and persists committed toggles', async () => {
    vi.mocked(CollapsibleSectionStateCache.isSet).mockReturnValue(true);
    vi.mocked(CollapsibleSectionStateCache.isExpanded).mockReturnValue(false);
    initializeApplication({ executions: [], pipelineConfigs: [{ id: 'pipeline-id' } as IPipeline] });
    await settleInitialization();

    expect(CollapsibleSectionStateCache.isSet).toHaveBeenCalledWith('insightFilters');
    expect(CollapsibleSectionStateCache.isExpanded).toHaveBeenCalledWith('insightFilters');
    const toggle = screen.getByRole('button', { name: 'Show filters' });
    expect(CollapsibleSectionStateCache.setExpanded).not.toHaveBeenCalled();

    act(() => {
      toggle.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      toggle.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(screen.getByRole('button', { name: 'Show filters' })).toBeVisible();
    expect(CollapsibleSectionStateCache.setExpanded).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Show filters' }));

    expect(screen.getByText('Filters')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Show filters' })).not.toBeInTheDocument();
    expect(component.container.querySelector('.insight')).toHaveClass('filters-expanded');
    expect(component.container.querySelector('.insight > .nav')).toBeInTheDocument();
    expect((CollapsibleSectionStateCache.setExpanded as Mock).mock.calls).toEqual([['insightFilters', true]]);
  });

  it('clears an unmatched manual execution param through the router state service', async () => {
    routerProps.stateParams = { startManualExecution: 'missing-pipeline' };
    initializeApplication({ executions: [], pipelineConfigs: [] });
    await settleInitialization();

    expect(routerProps.stateService.go).toHaveBeenCalledWith(
      '.',
      { startManualExecution: null },
      { inherit: true, location: 'replace' },
    );
  });

  it('starts a deep-linked manual execution from route params', async () => {
    const pipeline = { id: 'pipeline-id', name: 'Test Pipeline' } as IPipeline;
    routerProps.stateParams = { startManualExecution: pipeline.id };
    const showModal = vi.spyOn(ManualExecutionModal, 'show').mockReturnValue(Promise.reject());
    initializeApplication({ executions: [], pipelineConfigs: [pipeline] });
    await settleInitialization();

    expect(showModal).toHaveBeenCalledWith(expect.objectContaining({ application, pipeline }), runtimeServices);
  });
});
