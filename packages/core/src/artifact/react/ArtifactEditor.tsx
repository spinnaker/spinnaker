import { cloneDeep, head } from 'lodash';
import React from 'react';

import { ArtifactAccountSelector } from './ArtifactAccountSelector';
import { IArtifactAccount } from '../../account';
import { IArtifact, IPipeline } from '../../domain';
import { StageConfigField } from '../../pipeline/config/stages/common';
import {
  CUSTOM_ARTIFACT_ACCOUNT,
  TYPE as CUSTOM_TYPE,
} from '../../pipeline/config/triggers/artifacts/custom/CustomArtifactEditor';
import { Registry } from '../../registry';
import { UUIDGenerator } from '../../utils';

export interface IArtifactEditorProps {
  pipeline: IPipeline;
  artifact: IArtifact;
  artifactAccounts: IArtifactAccount[];
  onArtifactEdit: (artifact: IArtifact) => void;
  isDefault: boolean;
}

/**
 * Editor for either the match or default side of an expected artifact. Also
 * used in stages where an inline default artifact may be defined.
 */
export class ArtifactEditor extends React.Component<IArtifactEditorProps> {
  private onArtifactAccountChanged = (artifactAccount: IArtifactAccount) => {
    // reset artifact fields if the account type has changed, so we don't leave dangling properties
    // that are not modifiable using the new artifact account type's form.

    const artifact: IArtifact =
      this.props.artifact &&
      (!this.props.artifact.type ||
        artifactAccount.types.includes(this.props.artifact.type) ||
        artifactAccount.name === CUSTOM_ARTIFACT_ACCOUNT)
        ? cloneDeep(this.props.artifact)
        : { id: UUIDGenerator.generateUuid() };
    artifact.artifactAccount = artifactAccount.name;
    if (!artifact.type || artifactAccount.name !== CUSTOM_ARTIFACT_ACCOUNT) {
      artifact.type = artifactAccount.types && artifactAccount.types.length > 0 ? artifactAccount.types[0] : '';
    }
    this.props.onArtifactEdit(artifact);
  };

  private defaultArtifactAccountIfNecessary(): void {
    const { artifact, artifactAccounts } = this.props;
    if (!artifact.artifactAccount && artifactAccounts.length > 0) {
      this.onArtifactAccountChanged(
        head(artifactAccounts.filter((a) => a.types.includes(artifact.type))) ||
          head(artifactAccounts.filter((a) => a.types.includes(CUSTOM_TYPE))) ||
          head(artifactAccounts),
      );
    }
  }

  public componentDidMount(): void {
    this.defaultArtifactAccountIfNecessary();
  }

  public componentDidUpdate(): void {
    this.defaultArtifactAccountIfNecessary();
  }

  public render(): React.ReactNode {
    const { pipeline, artifact, artifactAccounts, onArtifactEdit, isDefault } = this.props;
    const artifactAccount =
      artifactAccounts.find((acc) => acc.name === artifact.artifactAccount) || artifactAccounts[0];
    const accountTypes = artifactAccount ? artifactAccount.types : undefined;
    const kinds = isDefault ? Registry.pipeline.getDefaultArtifactKinds() : Registry.pipeline.getMatchArtifactKinds();
    const kind = accountTypes ? kinds.find((a) => accountTypes.some((typ) => a.typePattern.test(typ))) : undefined;
    const EditCmp = kind && kind.editCmp;

    return (
      <>
        <StageConfigField label="Account" fieldColumns={8}>
          <ArtifactAccountSelector
            accounts={artifactAccounts}
            selected={artifactAccount}
            onChange={this.onArtifactAccountChanged}
          />
        </StageConfigField>
        {EditCmp && (
          <EditCmp account={artifactAccount} artifact={artifact} onChange={onArtifactEdit} pipeline={pipeline} />
        )}
      </>
    );
  }
}
