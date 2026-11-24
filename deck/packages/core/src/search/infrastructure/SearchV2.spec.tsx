import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { of } from 'rxjs';

import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { SearchStatus } from '../searchResult';
import {
  getInfrastructureSearchApiFilterParams,
  getInfrastructureSearchParamsFromFilters,
  SearchV2Component,
  selectSearchResultTab,
} from './SearchV2';

describe('SearchV2', () => {
  it('keeps only non-blank API filter parameters', () => {
    expect(
      getInfrastructureSearchApiFilterParams({
        key: 'instance-1',
        name: ' ',
        account: null,
        region: undefined,
        stack: 'test',
        tab: 'applications',
      }),
    ).toEqual({ key: 'instance-1', stack: 'test' });
  });

  it('selects the applications result first and waits for earlier unfinished tabs', () => {
    const type = (id: string, order: number) => ({ id, order, displayName: id });
    const finished = (id: string, order: number, results: any[]) => ({
      type: type(id, order),
      status: SearchStatus.FINISHED,
      results,
    });

    expect(selectSearchResultTab([finished('serverGroups', 1, [{}]), finished('applications', 10, [{}])] as any)).toBe(
      'applications',
    );
    expect(
      selectSearchResultTab([
        { type: type('clusters', 1), status: SearchStatus.SEARCHING, results: [] },
        finished('serverGroups', 2, [{}]),
      ] as any),
    ).toBeNull();
  });

  it('maps visible filters to replacement URL parameters', () => {
    expect(
      getInfrastructureSearchParamsFromFilters([
        { key: 'name', text: 'api' },
        { key: 'region', text: 'us-east-1' },
      ] as any),
    ).toEqual({
      key: undefined,
      name: 'api',
      account: undefined,
      region: 'us-east-1',
      stack: undefined,
    });
  });

  it('renders the search controls without a query', () => {
    const contextRouter = new UIRouterReact();
    contextRouter.plugin(servicesPlugin);
    contextRouter.plugin(hashLocationPlugin);
    const stateService = { go: vi.fn() };
    const router = { globals: { params$: of({}) } } as any;
    const services = {
      pageTitleService: { handleRoutingSuccess: vi.fn() },
      cacheInitializer: { refreshCaches: vi.fn() },
      infrastructureSearchService: {
        getSearcher: () => ({
          getCategoryConfig: () => ({}),
          formatRouteResult: vi.fn(),
        }),
      },
    } as any;

    const { unmount } = render(
      <DeckRuntimeContext.Provider value={{ services }}>
        <UIRouterContext.Provider value={contextRouter}>
          <SearchV2Component
            router={router}
            stateParams={{}}
            stateService={stateService as any}
            deckRuntimeServices={services}
          />
        </UIRouterContext.Provider>
      </DeckRuntimeContext.Provider>,
    );

    expect(screen.getByText('Search')).toBeInTheDocument();
    unmount();
    contextRouter.dispose();
  });
});
