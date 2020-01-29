import { IStage } from 'core/domain';
import { mockAwsCluster } from 'core/mocks';

export const mockImageFindStage: IStage = {
  cloudProvider: 'aws',
  cluster: 'deck-test',
  credentials: 'test',
  name: 'Find AMI in test cluster',
  onlyEnabled: true,
  refId: 1,
  requisiteStageRefIds: [],
  selectionStrategy: 'NEWEST',
  type: 'findImage',
};

export const mockDeployStage: IStage = {
  clusters: [mockAwsCluster],
  name: 'Deploy',
  refId: 2,
  requisiteStageRefIds: [1],
  type: 'deploy',
};

export const mockWaitStage: IStage = {
  name: 'Wait before promotion',
  refId: 3,
  requisiteStageRefIds: [2],
  type: 'wait',
  waitTime: 3000,
};
