import { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export interface IInstanceTypeSelectorProps {
  command: IServerGroupCommand;
  onTypeChanged: (type: string) => void;
}
