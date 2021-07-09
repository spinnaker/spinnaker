import { IServerGroupCommand } from './serverGroupCommandBuilder.service';

export interface IInstanceArchetypeSelectorProps {
  command: IServerGroupCommand;
  onProfileChanged: (profile: string) => void;
  onTypeChanged: (type: string) => void;
}
