import { IArtifact, IPipeline } from 'core/domain';
import { IArtifactAccount } from 'core/account';

export interface IArtifactEditorProps {
  account: IArtifactAccount;
  artifact: IArtifact;
  onChange: (a: IArtifact) => void;
  pipeline?: IPipeline;
}
