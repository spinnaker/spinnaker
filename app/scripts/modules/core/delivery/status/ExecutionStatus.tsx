import { IExecution } from 'core/domain';

export interface IExecutionStatusProps {
  execution: IExecution;
  toggleDetails: (stageIndex?: number) => void;
  showingDetails: boolean;
  standalone: boolean;
}
