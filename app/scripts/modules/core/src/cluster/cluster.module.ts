import { IQService, module } from 'angular';
import { StateService } from '@uirouter/angularjs';

import './ClusterSearchResultFormatter';
import { CLUSTER_ALLCLUSTERSGROUPINGS } from './allClustersGroupings.component';
import { ON_DEMAND_CLUSTER_PICKER_COMPONENT } from './onDemand/onDemandClusterPicker.component';
import { PostSearchResultSearcherRegistry } from 'core/search/searchResult/PostSearchResultSearcherRegistry';
import { SearchResultHydratorRegistry } from 'core/search/searchResult/SearchResultHydratorRegistry';
import { ClusterPostSearchResultSearcher } from './ClusterPostSearchResultSearcher';
import { ClusterSearchResultHydrator } from './ClusterSearchResultHydrator';
import { ApplicationReader } from 'core/application/service/application.read.service';

export const CLUSTER_MODULE = 'spinnaker.core.cluster';

module(CLUSTER_MODULE, [
  require('./allClusters.controller.js').name,
  CLUSTER_ALLCLUSTERSGROUPINGS,
  ON_DEMAND_CLUSTER_PICKER_COMPONENT,
])
  .run(($q: IQService, $state: StateService, applicationReader: ApplicationReader) => {
    'ngInject';
    PostSearchResultSearcherRegistry.register('clusters', 'serverGroups', new ClusterPostSearchResultSearcher($q, $state));
    SearchResultHydratorRegistry.register('clusters', new ClusterSearchResultHydrator(applicationReader));
  });
