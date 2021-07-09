import { module } from 'angular';
import { cloneDeep } from 'lodash';
import React from 'react';
import { react2angular } from 'react2angular';

import { ArtifactAccountSelector } from './ArtifactAccountSelector';
import {
  EXPECTED_ARTIFACT_KIND_SELECTOR_COMPONENT_REACT,
  ExpectedArtifactKindSelector,
} from './ExpectedArtifactKindSelector';
import {
  EXPECTED_ARTIFACT_SOURCE_SELECTOR_COMPONENT_REACT,
  ExpectedArtifactSourceSelector,
  IExpectedArtifactSourceOption,
} from './ExpectedArtifactSourceSelector';
import { IArtifactAccount } from '../../account';
import { IArtifact, IArtifactKindConfig, IExpectedArtifact, IPipeline } from '../../domain';
import { ExpectedArtifactService } from '../expectedArtifact.service';
import { StageConfigField } from '../../pipeline';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export interface IExpectedArtifactEditorProps {
  default?: IExpectedArtifact;
  kinds: IArtifactKindConfig[];
  sources: IExpectedArtifactSourceOption[];
  accounts: IArtifactAccount[];
  onArtifactChange?: (_a: IArtifact) => void;
  onSave: (e: IExpectedArtifactEditorSaveEvent) => void;
  showIcons?: boolean;
  showAccounts?: boolean;
  hidePipelineFields?: boolean;
  className?: string;
  pipeline?: IPipeline;
}

interface IExpectedArtifactEditorState {
  expectedArtifact: IExpectedArtifact;
  source?: IExpectedArtifactSourceOption;
  account?: IArtifactAccount;
}

export interface IExpectedArtifactEditorSaveEvent {
  expectedArtifact: IExpectedArtifact;
  source: IExpectedArtifactSourceOption;
  account: IArtifactAccount;
}

export class ExpectedArtifactEditor extends React.Component<
  IExpectedArtifactEditorProps,
  IExpectedArtifactEditorState
> {
  public static defaultProps = {
    showIcons: true,
    showAccounts: true,
    fieldColumns: 8,
    singleColumn: false,
  };

  constructor(props: IExpectedArtifactEditorProps) {
    super(props);
    const expectedArtifact = props.default ? cloneDeep(props.default) : ExpectedArtifactService.createEmptyArtifact();
    this.state = {
      expectedArtifact: expectedArtifact,
      source: props.sources[0],
      account: this.accountsForExpectedArtifact(expectedArtifact)[0],
    };
  }

  private onSave = () => {
    this.props.onSave({
      expectedArtifact: this.state.expectedArtifact,
      source: this.state.source,
      account: this.state.account,
    });
  };

  private accountsForExpectedArtifact(expectedArtifact: IExpectedArtifact): IArtifactAccount[] {
    const artifact = ExpectedArtifactService.artifactFromExpected(expectedArtifact);
    if (!artifact || !this.props.accounts) {
      return [];
    }
    return this.props.accounts.filter((a) => a.types.includes(artifact.type));
  }

  private onSourceChange = (e: IExpectedArtifactSourceOption) => {
    this.setState({ source: e });
  };

  private onKindChange = (kind: IArtifactKindConfig) => {
    const expectedArtifact = cloneDeep(this.state.expectedArtifact);
    expectedArtifact.matchArtifact.type = kind.type;
    // kind is deprecated; remove it from artifacts as they are updated
    delete expectedArtifact.matchArtifact.kind;
    expectedArtifact.matchArtifact.customKind = kind.customKind;
    const accounts = this.accountsForExpectedArtifact(expectedArtifact);
    this.setState({ expectedArtifact, account: accounts[0] });
  };

  private onAccountChange = (account: IArtifactAccount) => {
    const { expectedArtifact } = this.state;
    this.props.onArtifactChange &&
      this.props.onArtifactChange({
        artifactAccount: account.name,
        id: expectedArtifact.matchArtifact.id,
        reference: expectedArtifact.matchArtifact.name,
        type: expectedArtifact.matchArtifact.type,
      });
    this.setState({ account });
  };

  private onArtifactEdit = (artifact: IArtifact) => {
    const expectedArtifact = { ...this.state.expectedArtifact, matchArtifact: { ...artifact } };
    let account = this.state.account;
    if (this.state.expectedArtifact.matchArtifact.type !== artifact.type) {
      const accounts = this.accountsForExpectedArtifact(expectedArtifact);
      account = accounts[0];
    }
    this.props.onArtifactChange &&
      this.props.onArtifactChange({
        artifactAccount: account ? account.name : '',
        id: artifact.id,
        reference: artifact.name,
        type: artifact.type,
      });
    this.setState({ expectedArtifact, account });
  };

  private availableKinds = () => {
    const kinds = this.props.kinds || [];
    const accounts = this.props.accounts || [];
    if (this.props.showAccounts) {
      return kinds.filter((k) => k.customKind || accounts.find((a) => a.types.includes(k.type)));
    } else {
      return kinds.slice(0);
    }
  };

  private onArtifactDisplayNameEdit = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const displayName = event.target.value;
    const { expectedArtifact } = this.state;
    const newExpectedArtifact = {
      ...expectedArtifact,
      displayName,
    };
    this.setState({ expectedArtifact: newExpectedArtifact });
  };

  public render() {
    const { sources, showIcons, showAccounts, hidePipelineFields } = this.props;
    const { expectedArtifact, source, account } = this.state;
    const accounts = this.accountsForExpectedArtifact(expectedArtifact);
    const artifact = ExpectedArtifactService.artifactFromExpected(expectedArtifact);
    const kinds = this.availableKinds().sort((a, b) => a.label.localeCompare(b.label));
    const kind = ExpectedArtifactService.getKindConfig(artifact, false);
    const EditCmp = kind && kind.editCmp;
    return (
      <>
        {!hidePipelineFields && (
          <StageConfigField label="Display Name">
            <input
              className="form-control"
              value={expectedArtifact.displayName}
              onChange={this.onArtifactDisplayNameEdit}
            />
          </StageConfigField>
        )}
        {sources.length > 1 && (
          <StageConfigField label="Artifact Source">
            <ExpectedArtifactSourceSelector sources={sources} selected={source} onChange={this.onSourceChange} />
          </StageConfigField>
        )}
        <StageConfigField label="Artifact Kind">
          <ExpectedArtifactKindSelector
            kinds={kinds}
            selected={kind}
            onChange={this.onKindChange}
            showIcons={showIcons}
          />
        </StageConfigField>
        {showAccounts && (
          <StageConfigField label="Artifact Account">
            <ArtifactAccountSelector accounts={accounts} selected={account} onChange={this.onAccountChange} />
          </StageConfigField>
        )}
        {EditCmp && (
          <EditCmp
            account={account}
            artifact={artifact}
            onChange={this.onArtifactEdit}
            pipeline={this.props.pipeline}
          />
        )}
        {!hidePipelineFields && (
          <StageConfigField label="">
            <button onClick={this.onSave} type="button" className="btn btn-block btn-primary btn-sm">
              Confirm
            </button>
          </StageConfigField>
        )}
      </>
    );
  }
}

export const EXPECTED_ARTIFACT_EDITOR_COMPONENT_REACT = 'spinnaker.core.artifacts.expected.editor.react';
module(EXPECTED_ARTIFACT_EDITOR_COMPONENT_REACT, [
  EXPECTED_ARTIFACT_KIND_SELECTOR_COMPONENT_REACT,
  EXPECTED_ARTIFACT_SOURCE_SELECTOR_COMPONENT_REACT,
]).component(
  'expectedArtifactEditorReact',
  react2angular(withErrorBoundary(ExpectedArtifactEditor, 'expectedArtifactEditorReact'), [
    'default',
    'kinds',
    'sources',
    'accounts',
    'onSave',
    'showIcons',
    'showAccounts',
    'className',
    'pipeline',
  ]),
);
