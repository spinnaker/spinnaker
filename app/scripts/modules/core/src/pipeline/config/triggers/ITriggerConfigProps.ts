import { ITrigger } from 'core/domain';

export interface ITriggerConfigProps {
  fieldUpdated: () => void;
  trigger: ITrigger;
}
