import { module } from 'angular';

export const ALL_CLUSTERS_GROUPINGS_COMPONENT = 'spinnaker.core.cluster.allClustersGroupings.component';

module(ALL_CLUSTERS_GROUPINGS_COMPONENT, [])
.component('allClustersGroupings', {
  templateUrl: require('./allClustersGroupings.component.html'),
  bindings: {
    groups: '<',
    app: '<',
    sortFilters: '<',
    initialized: '<',
  }
});
