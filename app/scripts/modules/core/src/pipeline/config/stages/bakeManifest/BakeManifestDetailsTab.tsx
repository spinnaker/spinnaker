import * as React from 'react';

import {
  decodeUnicodeBase64,
  ExecutionDetailsSection,
  IArtifact,
  IExecutionDetailsSectionProps,
  ManifestYaml,
} from '@spinnaker/core';

export class BakeManifestDetailsTab extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'bakedManifest';

  public render() {
    const bakedArtifacts: IArtifact[] = (this.props.stage.context.artifacts || []).filter(
      (a: IArtifact) => a.type === 'embedded/base64',
    );
    return (
      <ExecutionDetailsSection name={this.props.name} current={this.props.current}>
        {bakedArtifacts.map((artifact, i) => (
          <ManifestYaml
            key={i}
            linkName={bakedArtifacts.length > 1 ? `Baked Manifest ${i} YAML` : 'Baked Manifest YAML'}
            manifestText={decodeUnicodeBase64(artifact.reference)}
            modalTitle="Baked Manifest"
          />
        ))}
      </ExecutionDetailsSection>
    );
  }
}
