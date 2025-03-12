import type { IOrchestratedItem } from './IOrchestratedItem';
import type { ITaskStep } from './ITaskStep';

export interface ITask extends IOrchestratedItem {
  application: string;
  id: string;
  name?: string;
  steps?: ITaskStep[];
  variables: ITaskVariable[];
  endTime: number;
  startTime: number;
  execution: any;
  history: any;
  poller?: PromiseLike<void>;
}

export interface ITaskVariable {
  key: string;
  value: any;
}
