import { ICronTrigger } from 'core/domain';

export interface ICronTriggerConfigProps {
  trigger: ICronTrigger;
  triggerUpdated: (trigger: Partial<ICronTrigger>) => void;
}
