import { IPromise, IQService } from 'angular';
import { StateService } from '@uirouter/angularjs';
import { uniqBy } from 'lodash';

import { urlBuilderRegistry } from 'core/navigation';
import { IPostSearchResultSearcher, ISearchResultSet, ISearchResultType, searchResultTypeRegistry } from 'core/search';
import { IServerGroupSearchResult } from 'core/serverGroup/serverGroupSearchResultType';

import { IClusterSearchResult } from './clusterSearchResultType';

export class ClusterPostSearchResultSearcher implements IPostSearchResultSearcher {
  private TYPE_ID = 'clusters';
  constructor(private $q: IQService, private $state: StateService) {}

  private makeSearchResult(serverGroup: IServerGroupSearchResult): IClusterSearchResult {
    const type = this.TYPE_ID;
    const urlBuilder = urlBuilderRegistry.getBuilder(type);
    const href = urlBuilder.build(Object.assign({ type }, serverGroup), this.$state);

    const { account, application, cluster, provider, stack } = serverGroup;
    return { account, application, cluster, provider, stack, displayName: cluster, href, type };
  }

  public getPostSearchResults(resultSet: ISearchResultSet<IServerGroupSearchResult>): IPromise<ISearchResultSet> {
    const inputs = resultSet.results;
    const type: ISearchResultType = searchResultTypeRegistry.get(this.TYPE_ID);

    // create cluster search results based on the server group search results
    const clusters = inputs.map(input => this.makeSearchResult(input));
    const results: IClusterSearchResult[] = uniqBy(clusters, sg => `${sg.account}-${sg.cluster}`);

    const { status, error } = resultSet;
    return this.$q.when({ status, error, type, results });
  }
}
