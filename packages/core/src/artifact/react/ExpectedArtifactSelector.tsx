import { module } from 'angular';
import React from 'react';
import { react2angular } from 'react2angular';

import { ARTIFACT_ACCOUNT_SELECTOR_COMPONENT_REACT } from './ArtifactAccountSelector';
import { ArtifactIcon } from './ArtifactIcon';
import { EXPECTED_ARTIFACT_KIND_SELECTOR_COMPONENT_REACT } from './ExpectedArtifactKindSelector';
import { EXPECTED_ARTIFACT_SOURCE_SELECTOR_COMPONENT_REACT } from './ExpectedArtifactSourceSelector';
import { IArtifact, IExpectedArtifact } from '../../domain';
import { ExpectedArtifactService } from '../expectedArtifact.service';
import { TetheredSelect } from '../../presentation';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export interface IExpectedArtifactSelectorProps {
  expectedArtifacts: IExpectedArtifact[];
  selected?: IExpectedArtifact;
  requestingNew?: boolean;
  onRequestCreate?: () => void;
  onChange: (_: IExpectedArtifact) => void;
  showIcons?: boolean;
  className?: string;
  offeredArtifactTypes?: RegExp[];
  excludedArtifactTypes?: RegExp[];
}

export interface IExpectedArtifactSelectorOption {
  value: string;
  expectedArtifact?: IExpectedArtifact;
  requestingNew: boolean;
}

type IExpectedArtifactFilter = (ea: IExpectedArtifact, a: IArtifact) => boolean;

export class ExpectedArtifactSelector extends React.Component<IExpectedArtifactSelectorProps> {
  public static defaultProps = {
    requestingNew: false,
    showIcons: true,
  };

  private renderOption = (e: IExpectedArtifactSelectorOption) => {
    if (!e.expectedArtifact && !e.requestingNew) {
      return <span />;
    }
    if (e.requestingNew) {
      return <span>Create new...</span>;
    } else {
      return (
        <span>
          {this.props.showIcons && <ArtifactIcon expectedArtifact={e.expectedArtifact} width="16" height="16" />}
          {e.expectedArtifact.displayName}
        </span>
      );
    }
  };

  private onChange = (e: IExpectedArtifactSelectorOption) => {
    if (e.requestingNew) {
      this.props.onRequestCreate();
    } else {
      this.props.onChange(e.expectedArtifact);
    }
  };

  private filterExpectedArtifacts(fn: IExpectedArtifactFilter): IExpectedArtifact[] {
    return (this.props.expectedArtifacts || []).filter((ea) => {
      const artifact = ExpectedArtifactService.artifactFromExpected(ea);
      if (!artifact) {
        return false;
      }
      return fn(ea, artifact);
    });
  }

  public getExpectedArtifacts(): IExpectedArtifact[] {
    return this.filterExpectedArtifacts((_expectedArtifact, artifact) => {
      const { offeredArtifactTypes, excludedArtifactTypes } = this.props;
      let isIncluded = true;
      let isExcluded = false;
      if (offeredArtifactTypes && offeredArtifactTypes.length > 0) {
        isIncluded = !!offeredArtifactTypes.find((patt) => patt.test(artifact.type));
      }
      if (excludedArtifactTypes && excludedArtifactTypes.length > 0) {
        isExcluded = !!excludedArtifactTypes.find((patt) => patt.test(artifact.type));
      }
      return isIncluded && !isExcluded;
    });
  }

  public render() {
    const options = this.getExpectedArtifacts().map((ea) => {
      return { value: ea.id, expectedArtifact: ea, requestingNew: false };
    });
    if (this.props.onRequestCreate) {
      options.push({ value: '', expectedArtifact: null, requestingNew: true });
    }
    const value = {
      value: this.props.selected ? this.props.selected.id : '',
      expectedArtifact: this.props.selected,
      requestingNew: this.props.requestingNew,
    };
    return (
      <TetheredSelect
        className={this.props.className || ''}
        options={options}
        optionRenderer={this.renderOption}
        value={value}
        valueRenderer={this.renderOption}
        onChange={this.onChange}
        clearable={false}
      />
    );
  }
}

export const EXPECTED_ARTIFACT_SELECTOR_COMPONENT_REACT = 'spinnaker.core.artifacts.expected.selector.react';
module(EXPECTED_ARTIFACT_SELECTOR_COMPONENT_REACT, [
  EXPECTED_ARTIFACT_KIND_SELECTOR_COMPONENT_REACT,
  EXPECTED_ARTIFACT_SOURCE_SELECTOR_COMPONENT_REACT,
  ARTIFACT_ACCOUNT_SELECTOR_COMPONENT_REACT,
]).component(
  'expectedArtifactSelectorReact',
  react2angular(withErrorBoundary(ExpectedArtifactSelector, 'expectedArtifactSelectorReact'), [
    'expectedArtifacts',
    'selected',
    'requestingNew',
    'onRequestCreate',
    'onChange',
    'showIcons',
    'className',
    'offeredArtifactTypes',
    'excludedArtifactTypes',
  ]),
);
