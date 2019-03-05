import * as React from 'react';
import { cloneDeep } from 'lodash';
import { IArtifactAccount } from 'core/account';
import { ArtifactAccountSelector } from 'core/artifact';
import { IArtifact, IPipeline } from 'core/domain';
import { StageConfigField } from 'core/pipeline/config/stages/common';
import { Registry } from 'core/registry';
import { UUIDGenerator } from 'core/utils';

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
      this.props.artifact && artifactAccount.types.includes(this.props.artifact.type)
        ? cloneDeep(this.props.artifact)
        : { id: UUIDGenerator.generateUuid() };
    artifact.artifactAccount = artifactAccount.name;
    this.props.onArtifactEdit(artifact);
  };

  public render(): React.ReactNode {
    const { pipeline, artifact, artifactAccounts, onArtifactEdit, isDefault } = this.props;
    const artifactAccount = artifactAccounts.find(acc => acc.name === artifact.artifactAccount) || artifactAccounts[0];
    const accountTypes = artifactAccount ? artifactAccount.types : undefined;
    const kinds = isDefault ? Registry.pipeline.getDefaultArtifactKinds() : Registry.pipeline.getMatchArtifactKinds();
    const kind = accountTypes ? kinds.find(a => accountTypes.some(typ => a.typePattern.test(typ))) : undefined;
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
