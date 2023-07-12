import React from 'react';

import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';
import { ARTIFACT_TYPE_EMBEDDED } from '../../../../domain';
import { ManifestYaml } from '../../../../manifest';
import { Overridable } from '../../../../overrideRegistry';
import { decodeUnicodeBase64 } from '../../../../utils';
import { getBakedArtifacts } from './utils/getBakedArtifacts';
import { getContentReference } from './utils/getContentReference';

@Overridable('bakeManifest.bakeManifestDetailsTab')
export class BakeManifestDetailsTab extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'bakedManifest';

  public render() {
    const { current, name, stage } = this.props;
    const bakedArtifacts = getBakedArtifacts(stage.context);

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
        {bakedArtifacts.map((artifact, i) => {
          const linkName = bakedArtifacts.length > 1 ? `Baked Manifest ${i} YAML` : 'Baked Manifest YAML';

          return artifact.type === ARTIFACT_TYPE_EMBEDDED ? (
            <ManifestYaml
              key={i}
              linkName={linkName}
              modalTitle="Baked Manifest"
              manifestText={decodeUnicodeBase64(artifact.reference)}
            />
          ) : (
            <ManifestYaml
              key={i}
              linkName={linkName}
              modalTitle="Baked Manifest"
              manifestUri={getContentReference(artifact.reference)}
            />
          );
        })}
      </ExecutionDetailsSection>
    );
  }
}
