import { uniqBy } from 'lodash';
import { IPromise, IQService } from 'angular';
import { StateService } from '@uirouter/angularjs';

import { urlBuilderRegistry } from 'core/navigation/urlBuilder.registry';
import {
  ISearchResultFormatter,
  searchResultFormatterRegistry
} from 'core/search/searchResult/searchResultFormatter.registry';
import { ISearchResultSet } from 'core/search/infrastructure/infrastructureSearch.service';
import { IClusterSearchResult } from 'core/search/searchResult/model/IClusterSearchResult';
import { IServerGroupSearchResult } from 'core/search/searchResult/model/IServerGroupSearchResult';
import { IPostSearchResultSearcher } from 'core/search/searchResult/PostSearchResultSearcherRegistry';

export class ClusterPostSearchResultSearcher implements IPostSearchResultSearcher<IServerGroupSearchResult> {

  constructor(private $q: IQService, private $state: StateService) {}

  public getPostSearchResults(inputs: IServerGroupSearchResult[] = []): IPromise<ISearchResultSet[]> {
    const type = 'clusters';

    // create clusters based on the server group search results
    const serverGroups = inputs.map((serverGroup: IServerGroupSearchResult) => {
      const { account, application, cluster, provider, stack } = serverGroup;
      const urlBuilder = urlBuilderRegistry.getBuilder(type);
      const href = urlBuilder.build(Object.assign({ type }, serverGroup), this.$state);

      return { account, application, cluster, displayName: cluster, href, provider, stack, type };
    });

    const clusters: IClusterSearchResult[] = uniqBy(serverGroups, sg => `${sg.account}-${sg.cluster}`);
    const formatter: ISearchResultFormatter = searchResultFormatterRegistry.get(type);

    return this.$q.when([{
      id: type,
      category: type,
      icon: formatter.icon,
      iconClass: '',
      order: formatter.order,
      results: clusters
    }]);
  }
}
