import { servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';
import { RecoilRoot } from 'recoil';

import { InsightLayout, isInsightDetailUrl, shouldHideInsightFilters, shouldShowDetailsView } from './InsightLayout';
import { CollapsibleSectionStateCache } from '../cache';
import { FilterCollapse } from '../filterModel/FilterCollapse';

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
    ({
      notFound: false,
      hasError: false,
      serverGroups,
      getDataSource: () => serverGroups,
    } as any);

  it('computes hidden filters from the current server group visibility state', () => {
    const currentState = { name: 'home.applications.application.insight.clusters' };

    expect(shouldHideInsightFilters(currentState, false)).toBe(false);
    expect(shouldHideInsightFilters(currentState, true)).toBe(true);
  });

  it('reacts to server group initialization and cleans up on app replacement and unmount', async () => {
    spyOn(CollapsibleSectionStateCache, 'isSet').and.returnValue(false);
    const firstServerGroups = new TestServerGroupsDataSource();
    const secondServerGroups = new TestServerGroupsDataSource();
    const firstApp = application(firstServerGroups);
    const secondApp = application(secondServerGroups);
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.stateRegistry.register({ name: 'clusters', url: '/clusters' });
    await router.stateService.go('clusters', {}, { location: false });
    const Harness = ({ app }: { app: any }) =>
      React.createElement(
        RecoilRoot,
        null,
        React.createElement(UIRouterContext.Provider, { value: router }, React.createElement(InsightLayout, { app })),
      );
    const wrapper = mount(React.createElement(Harness, { app: firstApp }));

    expect(wrapper.find(FilterCollapse).length).toBe(1);
    expect(wrapper.find('.insight > .nav').length).toBe(1);
    expect(wrapper.find('.ng-scope').length).toBe(0);
    expect(firstServerGroups.callbackCount()).toBe(1);

    act(() => firstServerGroups.emit(true));
    wrapper.update();

    expect(wrapper.find(FilterCollapse).length).toBe(0);
    expect(wrapper.find('.insight > .nav').length).toBe(0);

    act(() => wrapper.setProps({ app: secondApp }));
    wrapper.update();

    expect(firstServerGroups.callbackCount()).toBe(0);
    expect(secondServerGroups.callbackCount()).toBe(1);
    expect(wrapper.find(FilterCollapse).length).toBe(1);
    expect(wrapper.find('.insight > .nav').length).toBe(1);

    wrapper.unmount();
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
    spyOn(CollapsibleSectionStateCache, 'isSet').and.returnValue(true);
    spyOn(CollapsibleSectionStateCache, 'isExpanded').and.returnValue(true);
    const setExpanded = spyOn(CollapsibleSectionStateCache, 'setExpanded');
    const router = new UIRouterReact();
    const wrapper = mount(
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

    let filterCollapse = wrapper.find(FilterCollapse);
    expect(CollapsibleSectionStateCache.isSet).toHaveBeenCalledWith('insightFilters');
    expect(CollapsibleSectionStateCache.isExpanded).toHaveBeenCalledWith('insightFilters');
    expect(filterCollapse.prop('filtersExpanded')).toBe(true);
    expect(setExpanded).not.toHaveBeenCalled();
    const onToggle = filterCollapse.prop('onToggle') as (() => void) | undefined;
    expect(onToggle).toEqual(jasmine.any(Function));
    act(() => {
      onToggle?.();
      onToggle?.();
    });
    wrapper.update();

    filterCollapse = wrapper.find(FilterCollapse);
    expect(filterCollapse.prop('filtersExpanded')).toBe(true);
    expect(setExpanded).not.toHaveBeenCalled();

    act(() => onToggle?.());
    wrapper.update();

    filterCollapse = wrapper.find(FilterCollapse);
    expect(filterCollapse.prop('filtersExpanded')).toBe(false);
    expect(setExpanded.calls.allArgs()).toEqual([['insightFilters', false]]);

    wrapper.unmount();
    router.dispose();
  });
});
