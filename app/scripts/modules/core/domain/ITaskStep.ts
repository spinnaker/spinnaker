import {ITimedItem} from './IOrchestratedItem';

export interface ITaskStep extends ITimedItem {
  name: string;
  status: string;
}
