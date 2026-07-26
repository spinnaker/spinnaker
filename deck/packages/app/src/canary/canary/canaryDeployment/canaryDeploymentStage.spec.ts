import { Registry } from '@spinnaker/core';

import './canaryDeploymentStage';

describe('canaryDeploymentStage', () => {
  it('registers a direct React stage config component', () => {
    const config = Registry.pipeline.getStageTypes().find((stageType) => stageType.key === 'canaryDeployment');

    expect(config).toBeDefined();
    expect(config.component).toBeDefined();
    expect((config as any).templateUrl).toBeUndefined();
    expect((config as any).controller).toBeUndefined();
    expect((config as any).controllerAs).toBeUndefined();
  });
});
