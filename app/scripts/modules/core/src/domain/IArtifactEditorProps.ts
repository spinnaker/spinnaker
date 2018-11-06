import { IArtifact, IArtifactAccount } from 'core';

export interface IArtifactEditorProps {
  account: IArtifactAccount;
  artifact: IArtifact;
  onChange: (a: IArtifact) => void;
  labelColumns: number;
  fieldColumns: number;
  singleColumn?: boolean;
  groupClassName?: string;
}
