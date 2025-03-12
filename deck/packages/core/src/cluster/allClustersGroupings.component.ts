import { module } from 'angular';
import { react2angular } from 'react2angular';

import { AllClustersGroupings } from './AllClustersGroupings';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const CLUSTER_ALLCLUSTERSGROUPINGS = 'core.cluster.allclustergroupings';
module(CLUSTER_ALLCLUSTERSGROUPINGS, []).component(
  'allClustersGroupings',
  react2angular(withErrorBoundary(AllClustersGroupings, 'allClustersGroupings'), ['app', 'initialized']),
);
