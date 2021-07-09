import { module } from 'angular';

import { KUBERNETS_RAW_RESOURCE_DATA_SOURCE } from './rawResource.dataSource';
import { KUBERNETS_RAW_RESOURCE_STATES } from './rawResource.states';

export const KUBERNETS_RAW_RESOURCE_MODULE = 'spinnaker.kubernetes.rawresource';

module(KUBERNETS_RAW_RESOURCE_MODULE, [KUBERNETS_RAW_RESOURCE_DATA_SOURCE, KUBERNETS_RAW_RESOURCE_STATES]);
