import type { Application } from '../../../../application';
import type { IPipeline, IStage } from '../../../../domain';

export interface IStageConfigProps {
  application: Application;
  stage: IStage;
  pipeline: IPipeline;
  configuration?: any;
  stageFieldUpdated: () => void;
  updateStage: (changes: { [key: string]: any }) => void;
  updateStageField: (changes: { [key: string]: any }) => void;
}
