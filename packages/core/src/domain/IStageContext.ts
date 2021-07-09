import { IBuildDiffInfo } from './IBuildDiffInfo';

export interface IStageContext {
  buildInfo?: IBuildDiffInfo;
  freeFormDetails: string;
  stack: string;
  application: string;
}
