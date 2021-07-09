import { ICronTrigger } from '../../../../domain';

export interface ICronTriggerConfigProps {
  trigger: ICronTrigger;
  triggerUpdated: (trigger: Partial<ICronTrigger>) => void;
}
