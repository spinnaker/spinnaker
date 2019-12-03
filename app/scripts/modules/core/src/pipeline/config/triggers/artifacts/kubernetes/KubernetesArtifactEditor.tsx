import { cloneDeep } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifactEditorProps, IArtifactKindConfig } from 'core/domain';
import { StageConfigField } from 'core/pipeline';
import { SpelText } from 'core/widgets';

import { ArtifactEditor } from '../ArtifactEditor';

class KubernetesArtifactEditor extends ArtifactEditor {
  constructor(props: IArtifactEditorProps) {
    super(props, props.artifact.type || 'kubernetes/configMap');
  }

  private onReferenceChange = (reference: string) => {
    const clonedArtifact = cloneDeep(this.props.artifact);
    clonedArtifact.reference = reference;
    this.props.onChange(clonedArtifact);
  };

  private onTypeChange = (option: Option<string>) => {
    const clonedArtifact = cloneDeep(this.props.artifact);
    clonedArtifact.type = option.value;
    this.props.onChange(clonedArtifact);
  };

  public render() {
    return (
      <>
        <StageConfigField label="Resource Type">
          <Select
            options={[
              { label: 'ConfigMap', value: 'kubernetes/configMap' },
              { label: 'Deployment', value: 'kubernetes/deployment' },
              { label: 'ReplicaSet', value: 'kubernetes/replicaSet' },
              { label: 'Secret', value: 'kubernetes/secret' },
            ]}
            clearable={false}
            value={this.props.artifact.type}
            onChange={this.onTypeChange}
          />
        </StageConfigField>
        <StageConfigField label="Reference">
          <SpelText
            placeholder=""
            value={this.props.artifact.reference}
            onChange={this.onReferenceChange}
            pipeline={this.props.pipeline}
            docLink={true}
          />
        </StageConfigField>
      </>
    );
  }
}

export const KubernetesMatch: IArtifactKindConfig = {
  label: 'Kubernetes',
  typePattern: ArtifactTypePatterns.KUBERNETES,
  type: 'kubernetes/*',
  description: 'A kubernetes resource.',
  key: 'kubernetes',
  isDefault: false,
  isMatch: true,
  editCmp: KubernetesArtifactEditor,
};

export const KubernetesDefault: IArtifactKindConfig = {
  label: 'Kubernetes',
  typePattern: ArtifactTypePatterns.KUBERNETES,
  type: 'kubernetes/*',
  description: 'A kubernetes resource.',
  key: 'default.kubernetes',
  isDefault: true,
  isMatch: false,
  editCmp: KubernetesArtifactEditor,
};
