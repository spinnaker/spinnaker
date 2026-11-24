import { act, fireEvent, render, screen } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';
import { BehaviorSubject } from 'rxjs';

import { getAutoNavigationHref, SearchV1Component } from './SearchV1';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { RecentHistoryService } from '../../history';

describe('SearchV1', () => {
  let contextRouter: UIRouterReact;
  let params$: BehaviorSubject<Record<string, any>>;
  let query: ReturnType<typeof vi.fn>;
  let stateService: { go: ReturnType<typeof vi.fn> };
  let pageTitleService: { handleRoutingSuccess: ReturnType<typeof vi.fn> };

  const resultSet = (displayName: string, href = `/applications/${displayName}`) =>
    [
      {
        type: { id: 'applications', displayName: 'Applications', order: 1 },
        results: [{ displayName, href }],
      },
    ] as any;

  const renderSearch = (stateParams: Record<string, any> = params$.value) => {
    params$.next(stateParams);
    const searcher = {
      query,
      formatRouteResult: vi.fn().mockResolvedValue('Recent app'),
      getCategoryConfig: (category: string) => ({ id: category, displayName: category }),
    };
    const router = { globals: { params$ } } as any;
    const services = {
      infrastructureSearchService: { getSearcher: () => searcher },
      pageTitleService,
      cacheInitializer: { refreshCaches: vi.fn() },
    } as any;
    return render(
      <DeckRuntimeContext.Provider value={{ services }}>
        <UIRouterContext.Provider value={contextRouter}>
          <SearchV1Component
            router={router}
            stateParams={stateParams}
            stateService={stateService as any}
            deckRuntimeServices={services}
          />
        </UIRouterContext.Provider>
      </DeckRuntimeContext.Provider>,
    );
  };

  beforeEach(() => {
    vi.useFakeTimers();
    params$ = new BehaviorSubject({ q: null, route: null });
    query = vi.fn();
    stateService = { go: vi.fn() };
    pageTitleService = { handleRoutingSuccess: vi.fn() };
    contextRouter = new UIRouterReact();
    contextRouter.plugin(servicesPlugin);
    contextRouter.plugin(hashLocationPlugin);
  });

  afterEach(() => {
    params$.complete();
    contextRouter.dispose();
    window.history.replaceState(null, '', '/');
    vi.useRealTimers();
  });

  const completeDebounce = async () => {
    await act(async () => {
      vi.advanceTimersByTime(300);
      await Promise.resolve();
      await Promise.resolve();
    });
  };

  it('shows a minimum-length warning and keeps the query in the URL', () => {
    renderSearch();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search infrastructure' }), { target: { value: 'ab' } });

    expect(screen.getByText('Please enter at least 3 characters to begin searching.')).toBeInTheDocument();
    expect(query).not.toHaveBeenCalled();
    expect(stateService.go).toHaveBeenLastCalledWith('.', { q: 'ab', route: null }, { location: 'replace' });
  });

  it('requires three characters, debounces valid queries, and replaces q in the URL', async () => {
    query.mockResolvedValue([
      {
        type: { id: 'serverGroups', displayName: 'Server Groups', order: 1 },
        results: [{ href: '#/server', displayName: 'Server', provider: 'aws', type: 'serverGroups' }],
      },
    ]);
    renderSearch();
    const input = screen.getByRole('searchbox', { name: 'Search infrastructure' });

    fireEvent.change(input, { target: { value: 'ab' } });
    await completeDebounce();
    expect(query).not.toHaveBeenCalled();
    expect(screen.getByText('Please enter at least 3 characters to begin searching.')).toBeInTheDocument();

    fireEvent.change(input, { target: { value: 'server' } });
    await act(async () => vi.advanceTimersByTime(299));
    expect(query).not.toHaveBeenCalled();
    await completeDebounce();

    expect(query).toHaveBeenCalledTimes(1);
    expect(query).toHaveBeenCalledWith('server');
    expect(screen.queryByText('Please enter at least 3 characters to begin searching.')).not.toBeInTheDocument();
    expect(screen.getByText('Server Groups (1)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Server' })).toHaveAttribute('href', '#/server');
    expect(stateService.go).toHaveBeenLastCalledWith('.', { q: 'server', route: null }, { location: 'replace' });
  });

  it('renders the latest result and ignores a stale request', async () => {
    let resolveFirst: (value: any) => void;
    let resolveSecond: (value: any) => void;
    query
      .mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (resolveSecond = resolve)));
    renderSearch();
    const input = screen.getByRole('searchbox', { name: 'Search infrastructure' });

    fireEvent.change(input, { target: { value: 'first' } });
    await act(async () => vi.advanceTimersByTime(300));
    fireEvent.change(input, { target: { value: 'second' } });
    await act(async () => vi.advanceTimersByTime(300));
    await act(async () => resolveSecond!(resultSet('Second')));

    expect(screen.getByText('Second')).toBeInTheDocument();
    await act(async () => resolveFirst!(resultSet('First')));
    expect(screen.queryByText('First')).not.toBeInTheDocument();
    expect(screen.getByText('Second')).toBeInTheDocument();
  });

  it('renders a no-results message after a completed search', async () => {
    query.mockResolvedValue([]);
    renderSearch();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search infrastructure' }), {
      target: { value: 'missing' },
    });
    await act(async () => {
      vi.advanceTimersByTime(300);
      await Promise.resolve();
    });

    expect(screen.getByText('No results matching "missing".')).toBeInTheDocument();
  });

  it('ignores an in-flight result after the query becomes too short', async () => {
    let resolveSearch!: (value: any) => void;
    query.mockReturnValue(new Promise((resolve) => (resolveSearch = resolve)));
    renderSearch();
    const input = screen.getByRole('searchbox', { name: 'Search infrastructure' });

    fireEvent.change(input, { target: { value: 'first' } });
    await completeDebounce();
    fireEvent.change(input, { target: { value: 'ab' } });
    await act(async () => resolveSearch(resultSet('First')));

    expect(input).toHaveValue('ab');
    expect(screen.queryByText('First')).not.toBeInTheDocument();
    expect(stateService.go).not.toHaveBeenCalledWith('.', { q: 'first', route: null }, { location: 'replace' });
  });

  it('ignores a query completion after unmount', async () => {
    let resolveSearch!: (value: any) => void;
    query.mockReturnValue(new Promise((resolve) => (resolveSearch = resolve)));
    const rendered = renderSearch();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search infrastructure' }), {
      target: { value: 'pending' },
    });
    await completeDebounce();
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    rendered.unmount();
    consoleError.mockClear();
    await act(async () => resolveSearch(resultSet('Late')));

    expect(consoleError).not.toHaveBeenCalled();
  });

  it('reacts to routed query changes and consumes one-time navigation', async () => {
    query.mockResolvedValue(resultSet('Routed'));
    renderSearch();

    act(() => params$.next({ q: 'routed', route: null }));
    await completeDebounce();

    expect(screen.getByRole('searchbox', { name: 'Search infrastructure' })).toHaveValue('routed');
    expect(screen.getByText('Routed')).toBeInTheDocument();
    expect(stateService.go).toHaveBeenCalledWith('.', { q: 'routed', route: null }, { location: 'replace' });
  });

  it('auto-navigates only the initial route=true query and consumes the route flag', async () => {
    query
      .mockResolvedValueOnce(resultSet('Initial', '#/one-shot-result'))
      .mockResolvedValueOnce(resultSet('Later', '#/unexpected-result'));
    renderSearch({ q: 'initial', route: true });

    await completeDebounce();
    expect(window.location.hash).toBe('#/one-shot-result');
    expect(stateService.go).toHaveBeenCalledWith('.', { route: null }, { location: 'replace' });

    fireEvent.change(screen.getByRole('searchbox', { name: 'Search infrastructure' }), {
      target: { value: 'later' },
    });
    await completeDebounce();
    expect(window.location.hash).toBe('#/one-shot-result');
  });

  it('does not auto-navigate when input changes before the routed query completes', async () => {
    let resolveInitial!: (value: any) => void;
    let resolveLater!: (value: any) => void;
    query
      .mockReturnValueOnce(new Promise((resolve) => (resolveInitial = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (resolveLater = resolve)));
    renderSearch({ q: 'initial', route: true });
    await completeDebounce();

    fireEvent.change(screen.getByRole('searchbox', { name: 'Search infrastructure' }), {
      target: { value: 'later' },
    });
    await completeDebounce();
    await act(async () => resolveInitial(resultSet('Initial', '#/stale-initial')));
    await act(async () => resolveLater(resultSet('Later', '#/later')));

    expect(window.location.hash).toBe('');
    expect(screen.getByText('Later')).toBeInTheDocument();
  });

  it('consumes auto-navigation when routed query params change before the initial result', async () => {
    let resolveInitial!: (value: any) => void;
    let resolveLater!: (value: any) => void;
    query
      .mockReturnValueOnce(new Promise((resolve) => (resolveInitial = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (resolveLater = resolve)));
    renderSearch({ q: 'initial', route: true });
    await completeDebounce();

    act(() => params$.next({ q: 'later', route: null }));
    await completeDebounce();
    await act(async () => resolveInitial(resultSet('Initial', '#/stale-initial')));
    await act(async () => resolveLater(resultSet('Later', '#/later')));

    expect(window.location.hash).toBe('');
    expect(screen.getByRole('searchbox', { name: 'Search infrastructure' })).toHaveValue('later');
    expect(screen.getByText('Later')).toBeInTheDocument();
  });

  it('separates projects, ranks infrastructure results, and renders direct links', async () => {
    vi.spyOn(RecentHistoryService, 'getItems').mockImplementation((category) =>
      category === 'applications'
        ? ([{ id: 'recent', state: 'home.applications.application', params: {}, extraData: {} }] as any)
        : [],
    );
    query.mockResolvedValue([
      {
        type: { id: 'projects', displayName: 'Projects', order: 1 },
        results: [{ id: 'project', name: 'project', config: { applications: ['app'] } }],
      },
      {
        type: { id: 'applications', displayName: 'Applications', order: 2 },
        results: [
          { href: '#/z', displayName: 'Zeta app' },
          { href: '#/a', displayName: 'app alpha' },
        ],
      },
    ]);
    renderSearch();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search infrastructure' }), { target: { value: 'app' } });
    await completeDebounce();

    expect(screen.getByRole('heading', { name: 'Projects' })).toBeInTheDocument();
    expect(screen.getByText('project')).toBeInTheDocument();
    const first = screen.getByRole('link', { name: 'app alpha' });
    const second = screen.getByRole('link', { name: 'Zeta app' });
    expect(first).toHaveAttribute('href', '#/a');
    expect(second).toHaveAttribute('href', '#/z');
    expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByText('Recent app')).not.toBeInTheDocument();
  });

  it('renders recent history for a blank query without searching', async () => {
    vi.spyOn(RecentHistoryService, 'getItems').mockImplementation((category) =>
      category === 'applications'
        ? ([{ id: 'recent', state: 'home.applications.application', params: {}, extraData: {} }] as any)
        : [],
    );
    renderSearch();
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(query).not.toHaveBeenCalled();
    expect(screen.getByText('Recent app')).toBeInTheDocument();
  });

  it('selects a unique matching shortcut result for automatic navigation', () => {
    expect(getAutoNavigationHref('exact', 'exact', resultSet('Exact', '/exact'))).toBe('/exact');
    expect(getAutoNavigationHref('stale', 'exact', resultSet('Exact', '/exact'))).toBeNull();
    expect(getAutoNavigationHref('exact', 'exact', [...resultSet('One'), ...resultSet('Two')])).toBeNull();
  });
});
