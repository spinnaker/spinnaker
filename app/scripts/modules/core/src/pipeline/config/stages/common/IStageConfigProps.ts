import { Application } from 'core/application';
import { IStage } from 'core/domain';

export interface IStageConfigProps {
  application: Application;
  stage: IStage;
  configuration?: any;
  stageFieldUpdated: () => void;
  updateStageField: (changes: { [key: string]: any }) => void;
}
