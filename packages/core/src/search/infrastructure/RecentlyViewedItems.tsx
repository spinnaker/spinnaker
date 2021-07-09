import React from 'react';

import { ISearchResult, ISearchResultPodData, SearchResultPods } from './SearchResultPods';
import { IRecentHistoryEntry, RecentHistoryService } from '../../history';
import { useData } from '../../presentation/hooks';
import { ReactInjector } from '../../reactShims';
import { logger } from '../../utils';

export interface IChildComponentProps {
  results: ISearchResultPodData[];
  onRemoveItem?: (categoryName: string, itemId: string) => void;
  onRemoveProject?: (projectId: string) => void;
  onResultClick: (categoryName: string) => void;
}

export interface IRecentlyViewedItemsProps {
  Component: React.ComponentType<IChildComponentProps>;
  limit?: number;
}

export function RecentlyViewedItems(props: IRecentlyViewedItemsProps) {
  const { Component } = props;
  const categoryNames = ['projects', 'applications', 'loadBalancers', 'serverGroups', 'instances', 'securityGroups'];
  // useMemo to get a single searcher per mount. The Searcher immediately performs work when instantiated.
  const search = React.useMemo(() => ReactInjector.infrastructureSearchService.getSearcher(), []);

  /** fetches the displayName and adds it to the history entry */
  function getFullHistoryEntry(category: string, item: IRecentHistoryEntry): PromiseLike<ISearchResult> {
    const routeParams = { ...item.params, ...item.extraData };
    return search.formatRouteResult(category, routeParams).then((displayName) => ({ ...item, displayName }));
  }

  const { result, refresh } = useData(
    async () => {
      const getCategoryResults = async (category: string) => {
        const config = search.getCategoryConfig(category);
        const items = RecentHistoryService.getItems(category);
        const results = await Promise.all(items.map((item) => getFullHistoryEntry(category, item)));
        return { category, config, results };
      };

      const categories = await Promise.all(categoryNames.map((category) => getCategoryResults(category)));
      return categories.filter((category) => category.results.length);
    },
    [],
    [],
  );

  const handleRemoveItem = (categoryName: string, itemId: string) => {
    RecentHistoryService.removeItem(categoryName, itemId);
    refresh();
  };

  const handleResultClick = (categoryName: string): void => {
    logger.log({ category: 'Primary Search', action: `Recent item selected from ${categoryName}` });
  };

  return Component ? (
    <Component
      results={result}
      onRemoveItem={handleRemoveItem}
      onRemoveProject={(projectId) => handleRemoveItem('projects', projectId)}
      onResultClick={handleResultClick}
    />
  ) : (
    // Once RecentlyViewedItems is no longer rendered as part of any angular
    // templates, we can stop defaulting to SearchResultPods and require a component.
    <SearchResultPods
      results={result}
      onRemoveItem={handleRemoveItem}
      onRemoveProject={(projectId) => handleRemoveItem('projects', projectId)}
      onResultClick={handleResultClick}
    />
  );
}
