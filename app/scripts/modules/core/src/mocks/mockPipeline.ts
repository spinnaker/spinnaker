import { IPipeline, IPipelineLock } from 'core/domain';
import {
  mockPipelineNotification,
  mockWaitStage,
  mockDeployStage,
  mockImageFindStage,
  mockBuildTrigger,
} from 'core/mocks';

export const mockPipelineLock: IPipelineLock = {
  ui: false,
  allowUnlockUi: true,
};

export const mockPipeline: IPipeline = {
  application: 'deck',
  description: 'Pipeline for test env',
  disabled: false,
  id: 'random-sha-id',
  index: 0,
  keepWaitingPipelines: true,
  lastModifiedBy: 'testuser@test.com',
  locked: mockPipelineLock,
  limitConcurrent: true,
  name: 'Test Deploy',
  notifications: [mockPipelineNotification],
  parameterConfig: [],
  stages: [mockImageFindStage, mockDeployStage, mockWaitStage, { ...mockDeployStage, requisiteStageRefIds: [3] }],
  triggers: [mockBuildTrigger],
  updateTs: 1574880985024,
};
