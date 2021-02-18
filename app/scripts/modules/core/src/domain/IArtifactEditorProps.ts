import { IArtifactAccount } from 'core/account';

import { IArtifact } from './IArtifact';
import { IPipeline } from './IPipeline';

export interface IArtifactEditorProps {
  account: IArtifactAccount;
  artifact: IArtifact;
  onChange: (a: IArtifact) => void;
  pipeline?: IPipeline;
}

export interface IArtifactEditorState {
  [k: string]: any;
}
