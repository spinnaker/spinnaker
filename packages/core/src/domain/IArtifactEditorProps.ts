import type { IArtifact } from './IArtifact';
import type { IPipeline } from './IPipeline';
import type { IArtifactAccount } from '../account';

export interface IArtifactEditorProps {
  account: IArtifactAccount;
  artifact: IArtifact;
  onChange: (a: IArtifact) => void;
  pipeline?: IPipeline;
}

export interface IArtifactEditorState {
  [k: string]: any;
}
