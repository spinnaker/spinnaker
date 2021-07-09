import { IOrchestratedItem } from './IOrchestratedItem';

export interface ITaskStep extends IOrchestratedItem {
  name: string;
  status: string;
}
