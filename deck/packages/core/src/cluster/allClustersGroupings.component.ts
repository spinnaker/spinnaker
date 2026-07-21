import { module } from 'angular';

import { AllClustersGroupings } from './AllClustersGroupings';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const CLUSTER_ALLCLUSTERSGROUPINGS = 'core.cluster.allclustergroupings';
module(CLUSTER_ALLCLUSTERSGROUPINGS, []).component(
  'allClustersGroupings',
  angularComponentFromReact(AllClustersGroupings, 'allClustersGroupings', ['app', 'initialized']),
);
