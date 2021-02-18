import React from 'react';

import {
  ArtifactTypePatterns,
  IArtifact,
  IExpectedArtifact,
  IPipeline,
  IStage,
  StageArtifactSelector,
} from '@spinnaker/core';

export interface IManifestBindArtifact {
  expectedArtifactId?: string;
  artifact?: IArtifact;
}

export interface IManifestBindArtifactsSelectorProps {
  pipeline: IPipeline;
  stage: IStage;
  bindings?: IManifestBindArtifact[];
  onChangeBindings: (_: IManifestBindArtifact[]) => void;
}

export class ManifestBindArtifactsSelector extends React.Component<IManifestBindArtifactsSelectorProps> {
  private onChangeBinding = (index: number, binding: IManifestBindArtifact) => {
    const bindings = (this.props.bindings || []).slice(0);
    bindings[index] = binding;
    this.props.onChangeBindings(bindings);
  };

  private onRemoveBinding = (index: number) => {
    const bindings = (this.props.bindings || []).slice(0);
    bindings.splice(index, 1);
    this.props.onChangeBindings(bindings);
  };

  public render() {
    const { stage, pipeline, bindings } = this.props;

    const renderSelect = (i: number, binding?: IManifestBindArtifact) => {
      const key = (!binding && 'new') || binding.expectedArtifactId || (binding.artifact && binding.artifact.id);
      return (
        <div className="row" key={key}>
          <div className="col-md-10">
            <StageArtifactSelector
              pipeline={pipeline}
              stage={stage}
              expectedArtifactId={binding && binding.expectedArtifactId}
              artifact={!!binding && binding.artifact}
              onArtifactEdited={(artifact: IArtifact) => this.onChangeBinding(i, { artifact: artifact })}
              onExpectedArtifactSelected={(expectedArtifact: IExpectedArtifact) =>
                this.onChangeBinding(i, { expectedArtifactId: expectedArtifact.id })
              }
              excludedArtifactIds={bindings.map((b) => b.expectedArtifactId)}
              excludedArtifactTypePatterns={[ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE]}
            />
          </div>
          {binding && (
            <div className="col-md-2">
              <a className="glyphicon glyphicon-trash" onClick={() => this.onRemoveBinding(i)} />
            </div>
          )}
        </div>
      );
    };
    const renderSelectEditable = (binding: IManifestBindArtifact, i: number) => renderSelect(i, binding);

    return (
      <>
        {bindings.map(renderSelectEditable)}
        {renderSelect(bindings.length)}
      </>
    );
  }
}
