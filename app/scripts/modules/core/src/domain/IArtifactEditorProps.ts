import { IArtifact } from 'core/domain';
import { IArtifactAccount } from 'core/account';

export interface IArtifactEditorProps {
  account: IArtifactAccount;
  artifact: IArtifact;
  onChange: (a: IArtifact) => void;
  labelColumns: number;
  fieldColumns: number;
  singleColumn?: boolean;
  groupClassName?: string;
}
