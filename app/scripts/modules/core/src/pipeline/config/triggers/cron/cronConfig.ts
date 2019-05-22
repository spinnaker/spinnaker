import { ICronTrigger } from 'core/domain';

export interface ICronTriggerConfigProps {
  trigger: ICronTrigger;
  triggerUpdated: (trigger: ICronTrigger) => void;
}
