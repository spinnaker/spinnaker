import { Application } from 'core/application';
import { IStage } from 'core/domain';

export interface IStageConfigProps {
  application: Application;
  stage: IStage;
  stageFieldUpdated: () => void;
}
