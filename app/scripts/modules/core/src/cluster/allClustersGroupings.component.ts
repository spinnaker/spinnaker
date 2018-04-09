import { module } from 'angular';
import { react2angular } from 'react2angular';
import { AllClustersGroupings } from './AllClustersGroupings';

export const CLUSTER_ALLCLUSTERSGROUPINGS = 'core.cluster.allclustergroupings';
module(CLUSTER_ALLCLUSTERSGROUPINGS, []).component(
  'allClustersGroupings',
  react2angular(AllClustersGroupings, ['app', 'initialized']),
);
