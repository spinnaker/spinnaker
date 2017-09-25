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

  private static TYPE = 'clusters';

  constructor(private $q: IQService, private $state: StateService) {
  }

  public getPostSearchResults(inputs: IServerGroupSearchResult[] = []): IPromise<ISearchResultSet[]> {

    const clusters: IClusterSearchResult[] = inputs.map((serverGroup: IServerGroupSearchResult) => {
      const { account, application, cluster, detail, region, stack } = serverGroup;
      return {
        account,
        application,
        cluster,
        displayName: cluster,
        href: urlBuilderRegistry.getBuilder(ClusterPostSearchResultSearcher.TYPE).build({
          account,
          application,
          cluster,
          stack,
          detail,
          region,
          type: ClusterPostSearchResultSearcher.TYPE
        }, this.$state),
        provider: serverGroup.provider,
        stack,
        type: ClusterPostSearchResultSearcher.TYPE
      }
    });

    const formatter: ISearchResultFormatter = searchResultFormatterRegistry.get(ClusterPostSearchResultSearcher.TYPE);
    return this.$q.when([{
      id: ClusterPostSearchResultSearcher.TYPE,
      category: ClusterPostSearchResultSearcher.TYPE,
      icon: formatter.icon,
      iconClass: '',
      order: formatter.order,
      results: clusters
    }]);
  }
}
