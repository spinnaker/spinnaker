import { module } from 'angular';
import React from 'react';
import Select from 'react-select';
import { react2angular } from 'react2angular';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { ArtifactEditor } from './ArtifactEditor';
import { ArtifactIcon } from './ArtifactIcon';
import { AccountService, IArtifactAccount } from '../../account';
import { IArtifact, IExpectedArtifact, IPipeline, IStage } from '../../domain';
import { ExpectedArtifactService } from '../expectedArtifact.service';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export interface IStageArtifactSelectorProps {
  pipeline: IPipeline;
  stage: IStage;

  // one of these two will be defined by this selector
  expectedArtifactId?: string;
  artifact?: IArtifact;

  onExpectedArtifactSelected: (expectedArtifact: IExpectedArtifact) => void;
  onArtifactEdited: (artifact: IArtifact) => void;

  excludedArtifactIds?: string[];
  excludedArtifactTypePatterns?: RegExp[];

  renderLabel?: (reactNode: React.ReactNode) => React.ReactNode;
}

export interface IStageArtifactSelectorState {
  artifactAccounts: IArtifactAccount[];
}

const DEFINE_NEW_ARTIFACT = '__inline.artifact__';

export class StageArtifactSelector extends React.Component<IStageArtifactSelectorProps, IStageArtifactSelectorState> {
  private defineNewArtifactOption: IExpectedArtifact = {
    ...ExpectedArtifactService.createEmptyArtifact(),
    displayName: 'Define a new artifact...',
    id: DEFINE_NEW_ARTIFACT,
  };

  private destroy$ = new Subject();

  constructor(props: IStageArtifactSelectorProps) {
    super(props);

    this.state = {
      artifactAccounts: [],
    };
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  public componentDidMount(): void {
    const excludedPatterns = this.props.excludedArtifactTypePatterns;
    observableFrom(AccountService.getArtifactAccounts())
      .pipe(takeUntil(this.destroy$))
      .subscribe((artifactAccounts) => {
        this.setState({
          artifactAccounts: excludedPatterns
            ? artifactAccounts.filter(
                (account) => !account.types.some((typ) => excludedPatterns.some((typPattern) => typPattern.test(typ))),
              )
            : artifactAccounts,
        });
      });
  }

  private renderArtifact = (value: IExpectedArtifact) => {
    return (
      <span>
        {value.id !== DEFINE_NEW_ARTIFACT && (
          <ArtifactIcon
            type={
              (value.matchArtifact && value.matchArtifact.type) || (value.defaultArtifact && value.defaultArtifact.type)
            }
            width="16"
            height="16"
          />
        )}
        {value && value.displayName}
      </span>
    );
  };

  private onExpectedArtifactSelected = (value: IExpectedArtifact) => {
    if (value.id !== DEFINE_NEW_ARTIFACT) {
      this.props.onExpectedArtifactSelected(value);
    } else {
      this.props.onArtifactEdited(value.defaultArtifact);
    }
  };

  private onInlineArtifactChanged = (value: IArtifact) => {
    this.props.onArtifactEdited(value);
  };

  public render() {
    const { pipeline, stage, expectedArtifactId, artifact, excludedArtifactIds, renderLabel } = this.props;
    const expectedArtifacts = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(stage, pipeline);
    const expectedArtifact = expectedArtifactId
      ? expectedArtifacts.find((a) => a.id === expectedArtifactId)
      : artifact
      ? {
          id: DEFINE_NEW_ARTIFACT,
          displayName: 'Artifact from execution context',
          defaultArtifact: artifact,
        }
      : undefined;

    const options = [
      this.defineNewArtifactOption,
      ...expectedArtifacts.filter(
        (a: IExpectedArtifact) => !excludedArtifactIds || !excludedArtifactIds.includes(a.id),
      ),
    ];

    const select = (
      <Select
        clearable={false}
        options={options}
        value={expectedArtifact}
        optionRenderer={this.renderArtifact}
        valueRenderer={this.renderArtifact}
        onChange={this.onExpectedArtifactSelected}
        placeholder="Select an artifact..."
      />
    );

    return (
      <>
        <div className="sp-margin-m-bottom" data-test-id="Stage.artifactSelector">
          {renderLabel ? renderLabel(select) : select}
        </div>
        {!!artifact && (
          <ArtifactEditor
            pipeline={pipeline}
            artifact={artifact}
            artifactAccounts={this.state.artifactAccounts}
            onArtifactEdit={(edited: IArtifact) => this.onInlineArtifactChanged(edited)}
            isDefault={true}
          />
        )}
      </>
    );
  }
}

export const STAGE_ARTIFACT_SELECTOR_COMPONENT_REACT = 'spinnaker.core.artifacts.stage.artifact.selector.react';
module(STAGE_ARTIFACT_SELECTOR_COMPONENT_REACT, []).component(
  'stageArtifactSelectorReact',
  react2angular(withErrorBoundary(StageArtifactSelector, 'stageArtifactSelectorReact'), [
    'pipeline',
    'stage',
    'expectedArtifactId',
    'artifact',
    'onExpectedArtifactSelected',
    'onArtifactEdited',
    'excludedArtifactIds',
    'excludedArtifactTypePatterns',
  ]),
);
