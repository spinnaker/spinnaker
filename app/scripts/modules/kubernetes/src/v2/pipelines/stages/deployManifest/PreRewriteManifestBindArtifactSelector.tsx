import * as React from 'react';
import Select, { Option } from 'react-select';
import { find, map } from 'lodash';

import { ArtifactIcon, ExpectedArtifactService, IExpectedArtifact, IPipeline, IStage } from '@spinnaker/core';

import { IManifestBindArtifact } from './ManifestBindArtifactsSelector';

export interface IExpectedArtifactMultiSelectorProps {
  pipeline: IPipeline;
  stage: IStage;
  bindings: IManifestBindArtifact[];
  onChangeBindings: (bindings: IManifestBindArtifact[]) => void;
}

export class PreRewriteManifestBindArtifactSelector extends React.Component<IExpectedArtifactMultiSelectorProps> {
  private getAvailableExpectedArtifacts = (): IExpectedArtifact[] => {
    return ExpectedArtifactService.getExpectedArtifactsAvailableToStage(this.props.stage, this.props.pipeline);
  };

  private getOptions = (): Array<Option<string>> => {
    return map(this.getAvailableExpectedArtifacts(), a => ({
      label: a.displayName,
      value: a.id,
    }));
  };

  private onChange = (options: Array<Option<string>>): void => {
    this.props.onChangeBindings(
      map(options, o => ({
        expectedArtifactId: o.value,
      })),
    );
  };

  private renderArtifact = (option: Option<string>) => {
    const artifact = find(this.getAvailableExpectedArtifacts(), a => a.id === option.value);
    if (!artifact) {
      return null;
    }
    return (
      <span>
        <ArtifactIcon
          type={
            (artifact.matchArtifact && artifact.matchArtifact.type) ||
            (artifact.defaultArtifact && artifact.defaultArtifact.type)
          }
          width="16"
          height="16"
        />
        {artifact.displayName}
      </span>
    );
  };

  public render() {
    return (
      <Select
        clearable={false}
        multi={true}
        options={this.getOptions()}
        onChange={this.onChange}
        optionRenderer={this.renderArtifact}
        placeholder="Select an artifact..."
        value={this.props.bindings.filter(b => b.expectedArtifactId).map(b => b.expectedArtifactId)}
        valueRenderer={this.renderArtifact}
      />
    );
  }
}
