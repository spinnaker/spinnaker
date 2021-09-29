import type { IBuildTrigger } from '@spinnaker/core';

export const mockBuildTrigger: IBuildTrigger = {
  enabled: true,
  master: 'deck',
  job: 'test-job',
  project: 'deck',
  type: 'jenkins',
};
