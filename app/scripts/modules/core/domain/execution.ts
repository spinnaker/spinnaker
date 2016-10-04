import { Trigger } from './trigger.ts'

export interface Execution {
  trigger: Trigger;
  user: string;
}
