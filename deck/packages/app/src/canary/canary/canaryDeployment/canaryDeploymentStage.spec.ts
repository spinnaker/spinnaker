import { Registry } from '@spinnaker/core';

import './canaryDeploymentStage';

describe('canaryDeploymentStage', () => {
  it('registers a direct React stage config component', () => {
    const config = Registry.pipeline.getStageTypes().find((stageType) => stageType.key === 'canaryDeployment');

    expect(config).toBeDefined();
    expect(config.component).toBeDefined();
  });
});
