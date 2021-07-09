import { ComponentType, SFC } from 'react';
import { IArtifactEditorProps } from './IArtifactEditorProps';

export interface IArtifactKindConfig {
  label: string;
  typePattern: RegExp;
  description: string;
  isDefault: boolean;
  editCmp?: ComponentType<IArtifactEditorProps> | SFC<IArtifactEditorProps>;
  // Legacy artifacts properties
  type?: string;
  key: string;
  isMatch: boolean;
  customKind?: boolean;
}
