import { IBuildTrigger } from 'core/domain';

export const mockBuildTrigger: IBuildTrigger = {
  enabled: true,
  master: 'deck',
  job: 'test-job',
  project: 'deck',
  type: 'jenkins',
};
