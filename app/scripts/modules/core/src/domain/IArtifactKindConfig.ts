import { ComponentType, SFC } from 'react';
import { IArtifactEditorProps } from 'core/domain';

export interface IArtifactKindConfig {
  label: string;
  type?: string;
  description: string;
  key: string;
  isDefault: boolean;
  isMatch: boolean;
  customKind?: boolean;
  isPubliclyAccessible?: boolean;
  editCmp?: ComponentType<IArtifactEditorProps> | SFC<IArtifactEditorProps>;
  template: string;
  controller: Function;
  controllerAs?: string;
}
