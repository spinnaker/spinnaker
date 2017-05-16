import { module } from 'angular';
import { IStageContext } from 'core/domain';

export function clusterNameFilter(namingService: any): any {
  return function (input: IStageContext) {

    if (!input) {
      return 'n/a';
    }
    return namingService.getClusterName(input.application, input.stack, input.freeFormDetails);
  };
}

export const CLUSTER_NAME_FILTER = 'spinnaker.core.pipeline.clusterName.filter';
module(CLUSTER_NAME_FILTER, [])
  .filter('clusterName', clusterNameFilter);
