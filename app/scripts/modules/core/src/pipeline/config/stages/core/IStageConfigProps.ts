import { IStage } from 'core/domain';

export interface IStageConfigProps {
  stage: IStage;
  stageFieldUpdated: () => void;
}
