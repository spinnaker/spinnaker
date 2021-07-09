import { IArtifact } from './IArtifact';
import { IPipeline } from './IPipeline';
import { IArtifactAccount } from '../account';

export interface IArtifactEditorProps {
  account: IArtifactAccount;
  artifact: IArtifact;
  onChange: (a: IArtifact) => void;
  pipeline?: IPipeline;
}

export interface IArtifactEditorState {
  [k: string]: any;
}
