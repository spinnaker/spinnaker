import { size, uniqBy } from 'lodash';
import { IPromise, IQService } from 'angular';
import { StateService } from '@uirouter/angularjs';

import { IServerGroup } from 'core/domain/IServerGroup';
import { IComponentName, namingService } from 'core/naming/naming.service';
import { urlBuilderRegistry } from 'core/navigation/urlBuilder.registry';
import {
  ISearchResultFormatter,
  searchResultFormatterRegistry
} from 'core/search/searchResult/searchResultFormatter.registry';
import { ISearchResultSet } from 'core/search/infrastructure/infrastructureSearch.service';
import { IClusterSearchResult } from 'core/search/searchResult/model/IClusterSearchResult';
import { IServerGroupSearchResult } from 'core/search/searchResult/model/IServerGroupSearchResult';
import { IPostSearchResultSearcher } from 'core/search/searchResult/PostSearchResultSearcherRegistry';
import { ServerGroupReader } from 'core/serverGroup/serverGroupReader.service';

export class ClusterPostSearchResultSearcher implements IPostSearchResultSearcher<IServerGroupSearchResult> {

  private static TYPE = 'clusters';

  constructor(private $q: IQService,
              private $state: StateService,
              private serverGroupReader: ServerGroupReader) {
  }

  public getPostSearchResults(inputs: IServerGroupSearchResult[] = []): IPromise<ISearchResultSet[]> {

    // create clusters based on the server group search results
    const clusters: IClusterSearchResult[] = uniqBy(inputs.map((serverGroup: IServerGroupSearchResult) => {
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
    }), 'cluster');

    // for performance reasons, request each server group separately rather than all together and flag them as to
    // whether or not they exist.
    const requests: IPromise<IServerGroup>[] =
      inputs.map((serverGroup: IServerGroupSearchResult) => {
        return this.serverGroupReader.getServerGroup(serverGroup.application, serverGroup.account, serverGroup.region, serverGroup.serverGroup)
          .then((sg: IServerGroup) => {
            serverGroup.exists = true;
            return sg;
          })
          .catch(() => {
            serverGroup.exists = false;
            return null;
          });
      });
    return this.$q.all(requests)
      .then((serverGroups: IServerGroup[]) => {

        // creates a multimap by cluster, adding all the existing ASGs to its corresponding cluster.
        const clusterToServerGroupMap: { [key: string]: IServerGroup[] } =
          serverGroups.reduce((map: { [key: string]: IServerGroup[] }, serverGroup: IServerGroup) => {

            if (serverGroup) {
              const component: IComponentName = namingService.parseServerGroupName(serverGroup.name);
              let sgs: IServerGroup[] = map[component.cluster];
              if (!sgs) {
                sgs = map[component.cluster] = [];
              }
              sgs.push(serverGroup);
            }

            return map;
          }, {});
        clusters.forEach((cluster: IClusterSearchResult) => cluster.serverGroups = clusterToServerGroupMap[cluster.cluster]);

        // cull bad ASGs - ones that returned 404s above when we tried to get the details
        inputs.splice(0, Infinity, ...inputs.filter((sg: IServerGroupSearchResult) => sg.exists));

        // finally, create the cluster search result set culling clusters that have empty server groups
        const results: IClusterSearchResult[] = clusters.filter((cluster) => size(cluster.serverGroups) > 0);
        const formatter: ISearchResultFormatter = searchResultFormatterRegistry.get(ClusterPostSearchResultSearcher.TYPE);
        return this.$q.when([{
          id: ClusterPostSearchResultSearcher.TYPE,
          category: ClusterPostSearchResultSearcher.TYPE,
          icon: formatter.icon,
          iconClass: '',
          order: formatter.order,
          results
        }]);
      });
  }
}
