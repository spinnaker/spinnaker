import { IPromise } from 'angular';
import { IOrchestratedItem } from './IOrchestratedItem';
import { ITaskStep } from './ITaskStep';

export interface ITask extends IOrchestratedItem {
  id: string;
  name?: string;
  steps?: ITaskStep[];
  variables: ITaskVariable[];
  endTime: number;
  startTime: number;
  execution: any;
  history: any;
  poller?: IPromise<void>;
}

export interface ITaskVariable {
  key: string;
  value: any;
}
