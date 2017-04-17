import {ITimedItem} from './IOrchestratedItem';

export interface TaskStep extends ITimedItem {
  name: string;
  status: string;
}
