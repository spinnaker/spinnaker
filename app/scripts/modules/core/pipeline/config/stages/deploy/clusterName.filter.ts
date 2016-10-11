import { module } from 'angular';
import { StageContext } from 'core/domain/stageContext';

const MODULE_NAME = 'spinnaker.core.pipeline.clusterName.filter';

export function clusterNameFilter(namingService: any): any {
  return function (input: StageContext) {

    if (!input) {
      return 'n/a';
    }
    return namingService.getClusterName(input.application, input.stack, input.freeFormDetails);
    };
}

module(MODULE_NAME, [])
  .filter('clusterName', clusterNameFilter);

export default MODULE_NAME;
