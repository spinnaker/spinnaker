import {Trigger} from './trigger';
import {IStage} from "./IStage";

export interface IPipeline {
  application: string;
  id: string;
  index: number;
  keepWaitingPipelines: boolean;
  lastModifiedBy: string;
  limitConcurrent: boolean;
  name: string;
  parallel: boolean;
  triggers: Trigger[];
  stages: IStage[];
}
