import { module } from 'angular';
import React from 'react';
import { react2angular } from 'react2angular';

import { ArtifactIcon } from './ArtifactIcon';
import { IArtifactKindConfig } from '../../domain';
import { TetheredSelect } from '../../presentation';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export interface IExpectedArtifactKindSelectorProps {
  kinds: IArtifactKindConfig[];
  selected: IArtifactKindConfig;
  onChange: (_a: IArtifactKindConfig) => void;
  showIcons?: boolean;
  className?: string;
}

export interface IExpectedArtifactKindSelectorState {
  selected?: IExpectedArtifactKindSelectorOption;
}

export interface IExpectedArtifactKindSelectorOption {
  label: string;
  description: string;
  type: string;
  value: string;
}

export class ExpectedArtifactKindSelector extends React.Component<
  IExpectedArtifactKindSelectorProps,
  IExpectedArtifactKindSelectorState
> {
  public static defaultProps = {
    showIcons: true,
  };

  constructor(props: IExpectedArtifactKindSelectorProps) {
    super(props);
    this.state = {
      selected: this.optionFromKindConfig(props.selected),
    };
  }

  private renderOption = (o: IExpectedArtifactKindSelectorOption) => {
    return (
      <span>
        {this.props.showIcons && <ArtifactIcon type={o.type} width="16" height="16" />}
        {o.label} - {o.description}
      </span>
    );
  };

  private onChange = (option: IExpectedArtifactKindSelectorOption) => {
    const kind = this.props.kinds.find((k) => k.key === option.value);
    this.setState({ selected: option });
    this.props.onChange(kind);
  };

  private optionFromKindConfig = (ak: IArtifactKindConfig) => {
    if (!ak) {
      return null;
    }
    return {
      label: ak.label,
      description: ak.description,
      type: ak.type,
      value: ak.key,
    };
  };

  public render() {
    const options = this.props.kinds.map(this.optionFromKindConfig);
    const value = this.state.selected;
    return (
      <TetheredSelect
        className={this.props.className || ''}
        options={options}
        value={value}
        optionRenderer={this.renderOption}
        valueRenderer={this.renderOption}
        onChange={this.onChange}
        clearable={false}
      />
    );
  }
}

export const EXPECTED_ARTIFACT_KIND_SELECTOR_COMPONENT_REACT = 'spinnaker.core.artifacts.expected.kind.selector.react';
module(EXPECTED_ARTIFACT_KIND_SELECTOR_COMPONENT_REACT, []).component(
  'expectedArtifactKindSelectorReact',
  react2angular(withErrorBoundary(ExpectedArtifactKindSelector, 'expectedArtifactKindSelectorReact'), [
    'kinds',
    'selected',
    'onChange',
    'showIcons',
    'className',
  ]),
);
