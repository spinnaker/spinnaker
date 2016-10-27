import {module} from 'angular';
import {sessionAffinityModelToViewMap} from '../../configure/common/sessionAffinityNameMaps';

function gceSessionAffinityFilter() {
  return function (modelValue: string): string {
    return sessionAffinityModelToViewMap[modelValue] || modelValue;
  };
}

const moduleName = 'spinnaker.gce.loadBalancer.details.sessionAffinity.filter';

module(moduleName, [])
  .filter('gceSessionAffinityFilter', gceSessionAffinityFilter);

export default moduleName;
