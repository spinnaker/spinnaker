import { module } from 'angular';

import { IStageContext } from '../../../../domain';
import { NameUtils } from '../../../../naming';

export function clusterNameFilter(): any {
  return function (input: IStageContext) {
    if (!input) {
      return 'n/a';
    }
    return NameUtils.getClusterName(input.application, input.stack, input.freeFormDetails);
  };
}

export const CLUSTER_NAME_FILTER = 'spinnaker.core.pipeline.clusterName.filter';
module(CLUSTER_NAME_FILTER, []).filter('clusterName', clusterNameFilter);
