import { module } from 'angular';
import React from 'react';
import { react2angular } from 'react2angular';

import { IExpectedArtifact } from '../../domain';
import { TetheredSelect } from '../../presentation';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';
import { UUIDGenerator } from '../../utils';

export interface IExpectedArtifactSourceOption {
  source: {
    expectedArtifacts: IExpectedArtifact[];
    name: string;
  };
  value?: string;
  label: string;
}

export interface IExpectedArtifactSourceSelectorProps {
  sources: IExpectedArtifactSourceOption[];
  selected: IExpectedArtifactSourceOption;
  onChange: (o: IExpectedArtifactSourceOption) => void;
  className?: string;
}

export interface IExpectedArtifactSourceSelectorState {
  options: IExpectedArtifactSourceOption[];
}

export class ExpectedArtifactSourceSelector extends React.Component<
  IExpectedArtifactSourceSelectorProps,
  IExpectedArtifactSourceSelectorState
> {
  constructor(props: IExpectedArtifactSourceSelectorProps) {
    super(props);
    this.state = {
      options: props.sources.map((s) => {
        if (s.value) {
          return s;
        } else {
          return { ...s, value: UUIDGenerator.generateUuid() };
        }
      }),
    };
  }

  private renderOption = (option: IExpectedArtifactSourceOption) => {
    return <span>{option.label}</span>;
  };

  private onChange = (option: IExpectedArtifactSourceOption) => {
    const source = this.props.sources.find((s) => s.source === option.source);
    this.props.onChange(source);
  };

  public render() {
    return (
      <TetheredSelect
        className={this.props.className || ''}
        options={this.state.options}
        optionRenderer={this.renderOption}
        value={this.props.selected}
        valueRenderer={this.renderOption}
        onChange={this.onChange}
        clearable={false}
      />
    );
  }
}

export const EXPECTED_ARTIFACT_SOURCE_SELECTOR_COMPONENT_REACT =
  'spinnaker.core.artifacts.expected.source.selector.react';
module(EXPECTED_ARTIFACT_SOURCE_SELECTOR_COMPONENT_REACT, []).component(
  'expectedArtifactSourceSelectorReact',
  react2angular(withErrorBoundary(ExpectedArtifactSourceSelector, 'expectedArtifactSourceSelectorReact'), [
    'sources',
    'className',
    'onChange',
    'selected',
  ]),
);
