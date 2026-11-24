import { act, fireEvent, render, screen } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';
import { RecoilRoot } from 'recoil';

import { InsightLayout, isInsightDetailUrl, shouldHideInsightFilters, shouldShowDetailsView } from './InsightLayout';
import type { Application } from '../application';
import { CollapsibleSectionStateCache } from '../cache';

class TestServerGroupsDataSource {
  public fetchOnDemand: boolean;
  private callbacks: Array<() => void> = [];

  public constructor(fetchOnDemand = false) {
    this.fetchOnDemand = fetchOnDemand;
  }

  public onRefresh(callback: () => void): () => void {
    this.callbacks.push(callback);
    return () => {
      this.callbacks = this.callbacks.filter((candidate) => candidate !== callback);
    };
  }

  public emit(fetchOnDemand: boolean): void {
    this.fetchOnDemand = fetchOnDemand;
    this.callbacks.forEach((callback) => callback());
  }

  public callbackCount(): number {
    return this.callbacks.length;
  }
}

describe('InsightLayout', () => {
  const application = (serverGroups = new TestServerGroupsDataSource()) =>
    (({
      notFound: false,
      hasError: false,
      serverGroups,
      getDataSource: () => serverGroups,
    } as unknown) as Application);

  it('computes hidden filters from the current server group visibility state', () => {
    const currentState = { name: 'home.applications.application.insight.clusters' };

    expect(shouldHideInsightFilters(currentState, false)).toBe(false);
    expect(shouldHideInsightFilters(currentState, true)).toBe(true);
  });

  it('reacts to server group initialization and cleans up on app replacement and unmount', async () => {
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
    const firstServerGroups = new TestServerGroupsDataSource();
    const secondServerGroups = new TestServerGroupsDataSource();
    const firstApp = application(firstServerGroups);
    const secondApp = application(secondServerGroups);
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.stateRegistry.register({ name: 'clusters', url: '/clusters' });
    await router.stateService.go('clusters', {}, { location: false });
    const Harness = ({ app }: { app: Application }) =>
      React.createElement(
        RecoilRoot,
        null,
        React.createElement(UIRouterContext.Provider, { value: router }, React.createElement(InsightLayout, { app })),
      );
    const { container, rerender, unmount } = render(React.createElement(Harness, { app: firstApp }));

    expect(screen.getByText('Filters')).toBeInTheDocument();
    expect(container.querySelector('.insight > .nav')).toBeInTheDocument();
    expect(container.querySelector('.ng-scope')).not.toBeInTheDocument();
    expect(firstServerGroups.callbackCount()).toBe(1);

    act(() => firstServerGroups.emit(true));

    expect(screen.queryByText('Filters')).not.toBeInTheDocument();
    expect(container.querySelector('.insight > .nav')).not.toBeInTheDocument();

    rerender(React.createElement(Harness, { app: secondApp }));

    expect(firstServerGroups.callbackCount()).toBe(0);
    expect(secondServerGroups.callbackCount()).toBe(1);
    expect(screen.getByText('Filters')).toBeInTheDocument();
    expect(container.querySelector('.insight > .nav')).toBeInTheDocument();

    unmount();
    router.dispose();
    expect(secondServerGroups.callbackCount()).toBe(0);
  });

  it('shows the detail outlet when the active state targets an insight detail view', () => {
    expect(shouldShowDetailsView({ views: { 'detail@../insight': {} } })).toBe(true);
  });

  it('shows the detail outlet for nested insight detail states without a retained detail view key', () => {
    expect(
      shouldShowDetailsView({ name: 'home.applications.application.insight.clusters.instanceDetails', views: {} }),
    ).toBe(true);
  });

  it('does not show the detail outlet for master-only insight states', () => {
    expect(
      shouldShowDetailsView({ name: 'home.applications.application.insight.clusters', views: { nav: {}, master: {} } }),
    ).toBe(false);
  });

  it('recognizes hash routes that target insight detail panels', () => {
    expect(
      isInsightDetailUrl(
        'http://localhost:5173/#/applications/kubernetesapp/clusters/instanceDetails/kubernetes/pod-1',
      ),
    ).toBe(true);
    expect(isInsightDetailUrl('http://localhost:5173/#/applications/kubernetesapp/clusters')).toBe(false);
  });

  it('controls filter expansion from the cache and persists committed toggles', () => {
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(true);
    vi.spyOn(CollapsibleSectionStateCache, 'isExpanded').mockReturnValue(true);
    const setExpanded = vi.spyOn(CollapsibleSectionStateCache, 'setExpanded').mockReturnValue(undefined);
    const router = new UIRouterReact();
    const { container, unmount } = render(
      React.createElement(
        RecoilRoot,
        null,
        React.createElement(
          UIRouterContext.Provider,
          { value: router },
          React.createElement(InsightLayout, { app: application() }),
        ),
      ),
    );

    expect(CollapsibleSectionStateCache.isSet).toHaveBeenCalledWith('insightFilters');
    expect(CollapsibleSectionStateCache.isExpanded).toHaveBeenCalledWith('insightFilters');
    expect(container.querySelector('.insight')).toHaveClass('filters-expanded');
    expect(screen.getByText('Filters')).toBeInTheDocument();
    expect(setExpanded).not.toHaveBeenCalled();
    const hideFilters = container.querySelector('button.unpin') as HTMLButtonElement;
    act(() => {
      fireEvent.click(hideFilters);
      fireEvent.click(hideFilters);
    });

    expect(container.querySelector('.insight')).toHaveClass('filters-expanded');
    expect(setExpanded).not.toHaveBeenCalled();

    fireEvent.click(hideFilters);

    expect(container.querySelector('.insight')).toHaveClass('filters-collapsed');
    expect(screen.getByRole('button', { name: 'Show filters' })).toBeInTheDocument();
    expect(setExpanded.mock.calls).toEqual([['insightFilters', false]]);

    unmount();
    router.dispose();
  });
});
