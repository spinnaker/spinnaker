import type { Mock } from 'vitest';
import type { ShallowWrapper } from 'enzyme';
import { shallow } from 'enzyme';
import React from 'react';
import { BehaviorSubject } from 'rxjs';

import { SearchV1Component as SearchV1 } from './SearchV1';
import { SearchResult } from './SearchResult';
import type { ISearchResultSet } from './infrastructureSearch.service';
import { RecentlyViewedItems } from './RecentlyViewedItems';
import { SearchStatus } from '../searchResult';

function resultSet(id: string, results: any[]): ISearchResultSet {
  return {
    type: { id, displayName: id, iconClass: `icon-${id}`, order: 1 } as any,
    results,
    status: SearchStatus.FINISHED,
  };
}

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

// rxjs 7's real AsyncScheduler doesn't reliably advance under jasmine.clock()'s
// fake timers (a known, unresolved upstream issue: ReactiveX/rxjs#6382), so
// debounceTime is exercised with a real, short wait instead of a faked tick.
function tick(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

describe('SearchV1', () => {
  let params$: BehaviorSubject<any>;
  let query: Mock;
  let go: Mock;
  let wrapper: ShallowWrapper | undefined;
  const deckRuntimeServices = {
    infrastructureSearchService: { getSearcher: () => ({ query: (...args: any[]) => query(...args) }) },
    pageTitleService: { handleRoutingSuccess: vi.fn() },
  } as any;

  const renderSearch = () =>
    shallow(
      <SearchV1
        deckRuntimeServices={deckRuntimeServices}
        router={{ globals: { params$ } } as any}
        stateParams={params$.value}
        stateService={{ go } as any}
      />,
    );

  beforeEach(() => {
    params$ = new BehaviorSubject({ q: null, route: null });
    query = vi.fn().mockReturnValue(Promise.resolve([]));
    go = vi.fn();
  });

  afterEach(() => {
    wrapper?.unmount();
    params$.complete();
  });

  it('requires three characters, debounces valid queries, and replaces q in the URL', async () => {
    query.mockReturnValue(
      Promise.resolve([
        resultSet('serverGroups', [{ href: '#/server', displayName: 'Server', provider: 'aws', type: 'serverGroups' }]),
      ]),
    );
    wrapper = renderSearch();
    const instance = wrapper.instance() as SearchV1;

    instance.handleQueryChange('ab');
    await tick(350);
    expect(query).not.toHaveBeenCalled();
    expect(instance.state.showMinLengthWarning).toBe(true);

    instance.handleQueryChange('server');
    await tick(350);
    await Promise.resolve();
    await Promise.resolve();

    expect(query).toHaveBeenCalledWith('server');
    expect(instance.state.categories.map(({ type }) => type.id)).toEqual(['serverGroups']);
    expect(go).toHaveBeenCalledWith('.', { q: 'server', route: null }, { location: 'replace' });
  });

  it('ignores stale query completions and completions after unmount', async () => {
    let resolveFirst: (value: ISearchResultSet[]) => void;
    let resolveSecond: (value: ISearchResultSet[]) => void;
    let resolveThird: (value: ISearchResultSet[]) => void;
    query
      .mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (resolveSecond = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (resolveThird = resolve)));
    wrapper = renderSearch();
    const instance = wrapper.instance() as SearchV1;

    instance.handleQueryChange('first');
    await tick(350);
    instance.handleQueryChange('second');
    await tick(350);
    resolveSecond([resultSet('applications', [{ displayName: 'second' }])]);
    await Promise.resolve();
    await Promise.resolve();
    resolveFirst([resultSet('applications', [{ displayName: 'first' }])]);
    await Promise.resolve();

    expect(instance.state.categories[0].results[0].displayName).toBe('second');

    instance.handleQueryChange('third');
    await tick(350);
    wrapper.unmount();
    wrapper = undefined;
    resolveThird([]);
    await Promise.resolve();
  });

  it('ignores an in-flight result after the query becomes too short', async () => {
    let resolveSearch: (value: ISearchResultSet[]) => void;
    query.mockReturnValue(new Promise((resolve) => (resolveSearch = resolve)));
    wrapper = renderSearch();
    const instance = wrapper.instance() as SearchV1;

    instance.handleQueryChange('first');
    await tick(350);
    instance.handleQueryChange('ab');
    resolveSearch([resultSet('applications', [{ displayName: 'first' }])]);
    await Promise.resolve();
    await Promise.resolve();

    expect(instance.state.query).toBe('ab');
    expect(instance.state.categories).toEqual([]);
    expect(go).not.toHaveBeenCalledWith('.', { q: 'first', route: null }, { location: 'replace' });
  });

  it('separates projects, ranks infrastructure results, and renders direct result links', async () => {
    query.mockReturnValue(
      Promise.resolve([
        resultSet('projects', [{ name: 'project', config: { applications: ['app'] } }]),
        resultSet('applications', [
          { href: '#/z', displayName: 'Zeta app', provider: 'aws', type: 'applications' },
          { href: '#/a', displayName: 'app alpha', provider: 'aws', type: 'applications' },
        ]),
      ]),
    );
    wrapper = renderSearch();
    const instance = wrapper.instance() as SearchV1;
    instance.handleQueryChange('app');
    await tick(350);
    await Promise.resolve();
    await Promise.resolve();
    wrapper.update();

    expect(instance.state.projects[0].type.id).toBe('projects');
    expect(wrapper.find(SearchResult).at(0).prop('displayName')).toBe('app alpha');
    expect(wrapper.find(RecentlyViewedItems).exists()).toBe(false);
  });

  it('shows recent history for a blank query', () => {
    wrapper = renderSearch();

    expect(wrapper.find(RecentlyViewedItems).exists()).toBe(true);
    expect(query).not.toHaveBeenCalled();
  });

  it('navigates once when the initial routed query has exactly one result', async () => {
    params$.next({ q: 'initial', route: true });
    query
      .mockReturnValueOnce(
        Promise.resolve([resultSet('applications', [{ displayName: 'initial', href: '#/one-shot-result' }])]),
      )
      .mockReturnValueOnce(
        Promise.resolve([resultSet('applications', [{ displayName: 'later', href: '#/unexpected-result' }])]),
      );
    wrapper = renderSearch();
    const instance = wrapper.instance() as SearchV1;
    const navigateToResult = vi.spyOn<any>(instance, 'navigateToResult').mockReturnValue(undefined);

    await tick(350);
    await flushPromises();
    expect(navigateToResult).toHaveBeenCalledWith('#/one-shot-result');

    instance.handleQueryChange('later');
    await tick(350);
    await flushPromises();
    expect(navigateToResult).toHaveBeenCalledTimes(1);
  });

  it('consumes routed navigation when input changes before the initial search completes', async () => {
    let resolveInitial: (value: ISearchResultSet[]) => void;
    let resolveLater: (value: ISearchResultSet[]) => void;
    query
      .mockReturnValueOnce(new Promise((resolve) => (resolveInitial = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (resolveLater = resolve)));
    params$.next({ q: 'initial', route: true });
    wrapper = renderSearch();
    const instance = wrapper.instance() as SearchV1;
    const navigateToResult = vi.spyOn<any>(instance, 'navigateToResult').mockReturnValue(undefined);
    await tick(350);

    instance.handleQueryChange('later');
    await tick(350);
    resolveInitial([resultSet('applications', [{ displayName: 'initial', href: '#/stale-initial-result' }])]);
    await flushPromises();
    expect(navigateToResult).not.toHaveBeenCalled();

    resolveLater([resultSet('applications', [{ displayName: 'later', href: '#/later-result' }])]);
    await flushPromises();

    expect(query).toHaveBeenCalledTimes(2);
    expect(instance.state.searching).toBe(false);
    expect(navigateToResult).not.toHaveBeenCalled();
  });

  it('consumes routed navigation when dynamic query params change before the initial search completes', async () => {
    let resolveInitial: (value: ISearchResultSet[]) => void;
    let resolveLater: (value: ISearchResultSet[]) => void;
    query
      .mockReturnValueOnce(new Promise((resolve) => (resolveInitial = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (resolveLater = resolve)));
    params$.next({ q: 'initial', route: true });
    wrapper = renderSearch();
    const instance = wrapper.instance() as SearchV1;
    const navigateToResult = vi.spyOn<any>(instance, 'navigateToResult').mockReturnValue(undefined);
    await tick(350);

    params$.next({ q: 'later', route: null });
    await tick(350);
    resolveInitial([resultSet('applications', [{ displayName: 'initial', href: '#/stale-initial-result' }])]);
    await flushPromises();
    expect(navigateToResult).not.toHaveBeenCalled();

    resolveLater([resultSet('applications', [{ displayName: 'later', href: '#/later-result' }])]);
    await flushPromises();

    expect(query).toHaveBeenCalledTimes(2);
    expect(instance.state.searching).toBe(false);
    expect(navigateToResult).not.toHaveBeenCalled();
  });
});
