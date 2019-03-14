import * as React from 'react';
import { IArtifactEditorProps, IArtifactEditorState } from 'core/domain';
import { cloneDeep } from 'lodash';

export class ArtifactEditor extends React.Component<IArtifactEditorProps, IArtifactEditorState> {
  protected constructor(props: IArtifactEditorProps, type: string) {
    super(props);
    if (props.artifact.type !== type) {
      const clonedArtifact = cloneDeep(props.artifact);
      clonedArtifact.type = type;
      clonedArtifact.customKind = false;
      props.onChange(clonedArtifact);
    }
  }

  public onVersionChange = (version: string) => {
    const clonedArtifact = cloneDeep(this.props.artifact);
    clonedArtifact.version = version;
    this.props.onChange(clonedArtifact);
  };
}
