import { Trigger } from './trigger';
import {IOrchestratedItem} from './IOrchestratedItem';

export interface Execution extends IOrchestratedItem {
  trigger: Trigger;
  user: string;
}
