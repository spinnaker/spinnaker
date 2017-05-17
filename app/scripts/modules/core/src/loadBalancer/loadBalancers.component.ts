import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CLOUD_PROVIDER_REGISTRY } from 'core/cloudProvider/cloudProvider.registry';
import { LOAD_BALANCER_FILTER_MODEL } from './filter/loadBalancerFilter.model';
import { LOAD_BALANCER_FILTER_SERVICE } from './filter/loadBalancer.filter.service';
import { FILTER_TAGS_COMPONENT } from '../filterModel/filterTags.component';
import { PROVIDER_SELECTION_SERVICE } from 'core/cloudProvider/providerSelection/providerSelection.service';
import { LoadBalancers } from './LoadBalancers';

export const LOAD_BALANCERS_COMPONENT = 'spinnaker.core.loadBalancers.component';
module(LOAD_BALANCERS_COMPONENT, [
  require('angular-ui-bootstrap'),
  PROVIDER_SELECTION_SERVICE,
  FILTER_TAGS_COMPONENT,
  LOAD_BALANCER_FILTER_SERVICE,
  LOAD_BALANCER_FILTER_MODEL,
  CLOUD_PROVIDER_REGISTRY,
])
  .component('loadBalancers', react2angular(LoadBalancers, ['app']));
