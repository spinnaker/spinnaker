import React from 'react';
import { Observable, Subject } from 'rxjs';
import ReactGA from 'react-ga';

import { IRecentHistoryEntry, RecentHistoryService } from 'core/history';
import { ReactInjector } from 'core/reactShims';

import { ISearchResult, ISearchResultPodData, SearchResultPods } from './SearchResultPods';

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

export interface IRecentlyViewedItemsState {
  recentItems: ISearchResultPodData[];
}

export class RecentlyViewedItems extends React.Component<IRecentlyViewedItemsProps, IRecentlyViewedItemsState> {
  public state: IRecentlyViewedItemsState = { recentItems: [] };
  private categories = ['projects', 'applications', 'loadBalancers', 'serverGroups', 'instances', 'securityGroups'];
  private search = ReactInjector.infrastructureSearchService.getSearcher();

  private refresh$ = new Subject<string[]>();
  private destroy$ = new Subject();

  public componentWillUnmount() {
    this.destroy$.next();
  }

  private updateRecentItems() {
    this.refresh$.next(this.categories);
  }

  public componentDidMount() {
    this.refresh$
      .switchMap((categories: string[]) => {
        return Observable.forkJoin(
          categories.map((category) => {
            const config = this.search.getCategoryConfig(category);
            const items = RecentHistoryService.getItems(category);
            const promises = items.map((item) => this.getFullHistoryEntry(category, item));
            return Promise.all(promises).then((results) => ({
              category,
              config,
              results: this.props.limit ? results.slice(0, this.props.limit) : results,
            }));
          }),
        );
      })
      .map((recentItems) => {
        return recentItems.filter((item) => item.results.length);
      })
      .takeUntil(this.destroy$)
      .subscribe((recentItems) => {
        this.setState({ recentItems });
      });

    this.updateRecentItems();
  }

  /** fetches the displayName and adds it to the history entry */
  private getFullHistoryEntry(category: string, item: IRecentHistoryEntry): PromiseLike<ISearchResult> {
    const routeParams = { ...item.params, ...item.extraData };
    return this.search.formatRouteResult(category, routeParams).then((displayName) => ({ ...item, displayName }));
  }

  private handleRemoveProject = (projectId: string) => {
    RecentHistoryService.removeItem('projects', projectId);
    this.updateRecentItems();
  };

  private handleRemoveItem = (categoryName: string, itemId: string) => {
    RecentHistoryService.removeItem(categoryName, itemId);
    this.updateRecentItems();
  };

  private handleResultClick = (categoryName: string): void => {
    ReactGA.event({ category: 'Primary Search', action: `Recent item selected from ${categoryName}` });
  };

  public render() {
    const { Component } = this.props;

    return Component ? (
      <Component
        results={this.state.recentItems}
        onRemoveItem={this.handleRemoveItem}
        onRemoveProject={this.handleRemoveProject}
        onResultClick={this.handleResultClick}
      />
    ) : (
      // Once RecentlyViewedItems is no longer rendered as part of any angular
      // templates, we can stop defaulting to SearchResultPods and require a component.
      <SearchResultPods
        results={this.state.recentItems}
        onRemoveItem={this.handleRemoveItem}
        onRemoveProject={this.handleRemoveProject}
        onResultClick={this.handleResultClick}
      />
    );
  }
}
