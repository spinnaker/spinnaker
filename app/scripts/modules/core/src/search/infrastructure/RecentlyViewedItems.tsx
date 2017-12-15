import { IPromise } from 'angular';
import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';

import { IRecentHistoryEntry } from 'core/history';
import { ReactInjector } from 'core/reactShims';

import { ISearchResult, ISearchResultPodData, SearchResultPods } from './SearchResultPods';

export interface IRecentlyViewedItemsState {
  recentItems: ISearchResultPodData[];
}

@BindAll()
export class RecentlyViewedItems extends React.Component<{}, IRecentlyViewedItemsState> {
  public state: IRecentlyViewedItemsState = { recentItems: [] };
  private categories = ['projects', 'applications', 'loadBalancers', 'serverGroups', 'instances', 'securityGroups'];
  private recentHistoryService = ReactInjector.recentHistoryService;
  private search = ReactInjector.infrastructureSearchService.getSearcher();

  private refresh$ = new Subject<string[]>();

  public componentWillUnmount() {
    this.refresh$.complete();
  }

  private updateRecentItems() {
    this.refresh$.next(this.categories);
  }

  public componentDidMount() {
    this.refresh$.switchMap((categories: string[]) => {
      return Observable.forkJoin(categories.map(category => {
        const config = this.search.getCategoryConfig(category);
        const items = this.recentHistoryService.getItems(category);
        const promises = items.map(item => this.getFullHistoryEntry(category, item));
        return Promise.all(promises).then(results => ({ category, config, results }));
      }));
    }).map(recentItems => {
      return recentItems.filter(item => item.results.length)
    }).subscribe(recentItems => {
      this.setState({ recentItems })
    });

    this.updateRecentItems();
  }

  /** fetches the displayName and adds it to the history entry */
  private getFullHistoryEntry(category: string, item: IRecentHistoryEntry): IPromise<ISearchResult> {
    const routeParams = Object.assign({}, item.params, item.extraData);
    return this.search.formatRouteResult(category, routeParams)
      .then(displayName => ({ ...item, displayName }));
  };

  private handleRemoveProject(projectId: string) {
    this.recentHistoryService.removeItem('projects', projectId);
    this.updateRecentItems();
  }

  private handleRemoveItem(categoryName: string, itemId: string) {
    this.recentHistoryService.removeItem(categoryName, itemId);
    this.updateRecentItems();
  }

  public render() {
    return (
      <SearchResultPods
        results={this.state.recentItems}
        onRemoveItem={this.handleRemoveItem}
        onRemoveProject={this.handleRemoveProject}
      />
    );
  }
}
