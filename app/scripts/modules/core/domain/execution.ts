import {IOrchestratedItem} from './IOrchestratedItem';
import {ITrigger} from './ITrigger';

export interface Execution extends IOrchestratedItem {
  trigger: ITrigger;
  user: string;
}
