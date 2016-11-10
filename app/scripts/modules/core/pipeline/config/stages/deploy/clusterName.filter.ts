import {module} from 'angular';
import {StageContext} from 'core/domain/stageContext';

export function clusterNameFilter(namingService: any): any {
  return function (input: StageContext) {

    if (!input) {
      return 'n/a';
    }
    return namingService.getClusterName(input.application, input.stack, input.freeFormDetails);
  };
}

export const CLUSTER_NAME_FILTER = 'spinnaker.core.pipeline.clusterName.filter';
module(CLUSTER_NAME_FILTER, [])
  .filter('clusterName', clusterNameFilter);
