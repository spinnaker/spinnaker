import { REST } from '@spinnaker/core';

export function getCanaryAnalysisHistory(canaryDeploymentId: string) {
  return REST('/canaryDeployments').path(canaryDeploymentId, 'canaryAnalysisHistory').get();
}
