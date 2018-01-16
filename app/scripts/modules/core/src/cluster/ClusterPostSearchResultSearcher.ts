import { uniqBy } from 'lodash';
import { IPromise, IQService } from 'angular';
import { StateService } from '@uirouter/angularjs';

import { urlBuilderRegistry } from 'core/navigation/urlBuilder.registry';
import {
  ISearchResultType,
  searchResultTypeRegistry
} from 'core/search/searchResult/searchResultsType.registry';
import { ISearchResultSet } from 'core/search/infrastructure/infrastructureSearch.service';
import { IServerGroupSearchResult } from 'core/serverGroup/serverGroupSearchResultType';
import { IClusterSearchResult } from './clusterSearchResultType';
import { IPostSearchResultSearcher } from 'core/search/searchResult/PostSearchResultSearcherRegistry';

export class ClusterPostSearchResultSearcher implements IPostSearchResultSearcher<IServerGroupSearchResult> {
  private TYPE_ID = 'clusters';
  constructor(private $q: IQService, private $state: StateService) {}

  private makeSearchResult(serverGroup: IServerGroupSearchResult): IClusterSearchResult {
    const type = this.TYPE_ID;
    const urlBuilder = urlBuilderRegistry.getBuilder(type);
    const href = urlBuilder.build(Object.assign({ type }, serverGroup), this.$state);

    const { account, application, cluster, provider, stack } = serverGroup;
    return { account, application, cluster, provider, stack, displayName: cluster, href, type };
  }

  public getPostSearchResults(inputs: IServerGroupSearchResult[] = []): IPromise<ISearchResultSet[]> {
    const type: ISearchResultType = searchResultTypeRegistry.get(this.TYPE_ID);

    // create cluster search results based on the server group search results
    const clusters = inputs.map(input => this.makeSearchResult(input));
    const results: IClusterSearchResult[] = uniqBy(clusters, sg => `${sg.account}-${sg.cluster}`);

    return this.$q.when([{ type, results }]);
  }
}
