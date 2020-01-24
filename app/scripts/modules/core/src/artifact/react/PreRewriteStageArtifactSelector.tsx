import React from 'react';
import { Observable, Subject } from 'rxjs';
import { find, get } from 'lodash';

import {
  defaultExcludedArtifactTypes,
  AccountService,
  ArtifactAccountSelector,
  ExpectedArtifactEditor,
  ExpectedArtifactSelector,
  ExpectedArtifactService,
  IArtifactAccount,
  IArtifactKindConfig,
  IArtifactSource,
  IExpectedArtifact,
  IExpectedArtifactSourceOption,
  IPipeline,
  IStage,
  Registry,
  StageConfigField,
} from 'core';

export interface IPreRewriteArtifactSelectorProps {
  excludedArtifactTypePatterns?: RegExp[];
  fieldColumns?: number;
  helpKey?: string;
  label: string;
  selectedArtifactId: string;
  pipeline: IPipeline;
  selectedArtifactAccount: string;
  setArtifactAccount: (accountName: string) => void;
  setArtifactId: (expectedArtifactId: string) => void;
  stage: IStage;
  updatePipeline: (pipeline: IPipeline) => void;
}

interface IPreRewriteArtifactSelectorState {
  accountsForArtifact: IArtifactAccount[];
  allArtifactAccounts: IArtifactAccount[];
  expectedArtifacts: IExpectedArtifact[];
  showCreateArtifactForm: boolean;
}

export class PreRewriteStageArtifactSelector extends React.Component<
  IPreRewriteArtifactSelectorProps,
  IPreRewriteArtifactSelectorState
> {
  public static defaultProps: Partial<IPreRewriteArtifactSelectorProps> = {
    excludedArtifactTypePatterns: defaultExcludedArtifactTypes,
  };
  private destroy$ = new Subject();

  public constructor(props: IPreRewriteArtifactSelectorProps) {
    super(props);
    this.state = {
      accountsForArtifact: [],
      allArtifactAccounts: [],
      expectedArtifacts: ExpectedArtifactService.getExpectedArtifactsAvailableToStage(props.stage, props.pipeline),
      showCreateArtifactForm: false,
    };
  }

  public componentDidMount(): void {
    this.fetchArtifactAccounts();
  }

  private fetchArtifactAccounts = (): void => {
    Observable.fromPromise(AccountService.getArtifactAccounts())
      .takeUntil(this.destroy$)
      .subscribe((allArtifactAccounts: IArtifactAccount[]) => {
        this.setState({
          accountsForArtifact: this.getAccountsForArtifact(allArtifactAccounts, this.props.selectedArtifactId),
          allArtifactAccounts,
        });
      });
  };

  private getAccountsForArtifact = (
    allArtifactAccounts: IArtifactAccount[],
    selectedArtifactId: string,
  ): IArtifactAccount[] => {
    const artifact = ExpectedArtifactService.artifactFromExpected(this.getSelectedExpectedArtifact(selectedArtifactId));
    if (!artifact) {
      return [];
    }
    return artifact.type === 'helm/chart'
      ? allArtifactAccounts.filter(a => a.types.includes(artifact.type) && a.name === artifact.artifactAccount)
      : allArtifactAccounts.filter(a => a.types.includes(artifact.type));
  };

  private getSelectedExpectedArtifact = (selectedArtifactId: string): IExpectedArtifact => {
    return find(this.state.expectedArtifacts, artifact => artifact.id === selectedArtifactId);
  };

  private getSelectedArtifactAccount = (): IArtifactAccount => {
    return find(this.state.allArtifactAccounts, account => account.name === this.props.selectedArtifactAccount);
  };

  private updateAccounts = (artifactId: string): void => {
    const { selectedArtifactAccount, setArtifactAccount } = this.props;

    const accountsForArtifact = this.getAccountsForArtifact(this.state.allArtifactAccounts, artifactId);
    if (!selectedArtifactAccount || !accountsForArtifact.find(a => a.name === selectedArtifactAccount)) {
      if (accountsForArtifact.length) {
        setArtifactAccount(get(accountsForArtifact, [0, 'name']));
      } else {
        setArtifactAccount(null);
      }
    }
    this.setState({
      accountsForArtifact,
    });
  };

  private onArtifactChange = (expectedArtifact: IExpectedArtifact) => {
    this.props.setArtifactId(expectedArtifact.id);
    this.setState({
      showCreateArtifactForm: false,
    });
    this.updateAccounts(expectedArtifact.id);
  };

  private onRequestCreateArtifact = (): void => {
    this.setState({
      showCreateArtifactForm: true,
    });
  };

  private showAccountSelect = (): boolean => {
    return (
      !this.state.showCreateArtifactForm &&
      this.getSelectedExpectedArtifact(this.props.selectedArtifactId) != null &&
      this.state.accountsForArtifact.length > 1
    );
  };

  private getKinds = (): IArtifactKindConfig[] => {
    return Registry.pipeline
      .getMatchArtifactKinds()
      .filter((a: IArtifactKindConfig) => !this.props.excludedArtifactTypePatterns.find(t => t.test(a.type)));
  };

  private getSources = (): IExpectedArtifactSourceOption[] => {
    return ExpectedArtifactService.sourcesForPipelineStage(
      () => this.props.pipeline,
      this.props.stage,
    ) as IExpectedArtifactSourceOption[];
  };

  private saveCreatedArtifact = (event: {
    expectedArtifact: IExpectedArtifact;
    account: IArtifactAccount;
    source: IArtifactSource<any>;
  }) => {
    ExpectedArtifactService.addArtifactTo(event.expectedArtifact, event.source.source);
    this.props.updatePipeline(event.source.source);
    this.updateAccounts(event.expectedArtifact.id);
    this.props.setArtifactId(event.expectedArtifact.id);
    this.props.setArtifactAccount(event.account.name);
    this.setState({
      expectedArtifacts: ExpectedArtifactService.getExpectedArtifactsAvailableToStage(
        this.props.stage,
        this.props.pipeline,
      ),
      showCreateArtifactForm: false,
    });
  };

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  public render() {
    const {
      excludedArtifactTypePatterns,
      fieldColumns,
      helpKey,
      label,
      selectedArtifactId,
      setArtifactAccount,
    } = this.props;
    const { accountsForArtifact, allArtifactAccounts, showCreateArtifactForm } = this.state;

    return (
      <>
        <StageConfigField helpKey={helpKey} label={label || 'Artifact'} fieldColumns={fieldColumns}>
          <ExpectedArtifactSelector
            excludedArtifactTypes={excludedArtifactTypePatterns}
            expectedArtifacts={this.state.expectedArtifacts}
            selected={this.getSelectedExpectedArtifact(selectedArtifactId)}
            onChange={this.onArtifactChange}
            onRequestCreate={this.onRequestCreateArtifact}
            requestingNew={showCreateArtifactForm}
          />
        </StageConfigField>
        {this.showAccountSelect() && (
          <StageConfigField label="Artifact Account">
            <ArtifactAccountSelector
              accounts={accountsForArtifact}
              selected={this.getSelectedArtifactAccount()}
              onChange={(account: IArtifactAccount) => setArtifactAccount(account.name)}
            />
          </StageConfigField>
        )}
        {this.state.showCreateArtifactForm && (
          <StageConfigField label="" fieldColumns={fieldColumns}>
            <ExpectedArtifactEditor
              accounts={allArtifactAccounts}
              fieldColumns={fieldColumns}
              kinds={this.getKinds()}
              onSave={this.saveCreatedArtifact}
              sources={this.getSources()}
            />
          </StageConfigField>
        )}
      </>
    );
  }
}
