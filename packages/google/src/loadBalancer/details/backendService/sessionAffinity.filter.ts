import { module } from 'angular';
import { sessionAffinityModelToViewMap } from '../../configure/common/sessionAffinityNameMaps';

function gceSessionAffinityFilter() {
  return function (modelValue: string): string {
    return sessionAffinityModelToViewMap[modelValue] || modelValue;
  };
}

export const SESSION_AFFINITY_FILTER = 'spinnaker.gce.loadBalancer.details.sessionAffinity.filter';
module(SESSION_AFFINITY_FILTER, []).filter('gceSessionAffinityFilter', gceSessionAffinityFilter);
