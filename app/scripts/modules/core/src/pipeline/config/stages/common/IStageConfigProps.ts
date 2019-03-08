import { Application } from 'core/application';
import { IPipeline, IStage } from 'core/domain';

export interface IStageConfigProps {
  application: Application;
  stage: IStage;
  pipeline: IPipeline;
  configuration?: any;
  stageFieldUpdated: () => void;
  updateStageField: (changes: { [key: string]: any }) => void;
}
