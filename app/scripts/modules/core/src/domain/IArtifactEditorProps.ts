import { IArtifact } from 'core';

export interface IArtifactEditorProps {
  artifact: IArtifact;
  onChange: (a: IArtifact) => void;
  labelColumns: number;
  fieldColumns: number;
  singleColumn?: boolean;
  groupClassName?: string;
}
