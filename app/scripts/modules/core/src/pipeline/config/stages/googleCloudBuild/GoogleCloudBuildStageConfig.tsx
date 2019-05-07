import * as React from 'react';
import { Observable, Subject } from 'rxjs';
import { cloneDeep } from 'lodash';

import { FormikStageConfig, IgorService, IStage, IStageConfigProps } from '@spinnaker/core';

import { GoogleCloudBuildStageForm, buildDefinitionSources } from './GoogleCloudBuildStageForm';
import { validate } from './googleCloudBuildValidators';

interface IGoogleCloudBuildStageConfigState {
  googleCloudBuildAccounts: string[];
}

export class GoogleCloudBuildStageConfig extends React.Component<IStageConfigProps, IGoogleCloudBuildStageConfigState> {
  private stage: IStage;
  private destroy$ = new Subject();

  public constructor(props: IStageConfigProps) {
    super(props);
    this.state = {
      googleCloudBuildAccounts: [],
    };
    const { stage: initialStageConfig } = props;
    const stage = cloneDeep(initialStageConfig);
    if (initialStageConfig.isNew) {
      stage.application = props.application.name;
      stage.buildDefinitionSource = buildDefinitionSources.TEXT;
    }
    // Intentionally initializing the stage config only once in the constructor
    // The stage config is then completely owned within FormikStageConfig's Formik state
    this.stage = stage;
  }

  public componentDidMount = (): void => {
    this.fetchGoogleCloudBuildAccounts();
  };

  private fetchGoogleCloudBuildAccounts = () => {
    Observable.fromPromise(IgorService.getGcbAccounts())
      .takeUntil(this.destroy$)
      .subscribe((googleCloudBuildAccounts: string[]) => {
        this.setState({ googleCloudBuildAccounts });
      });
  };

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  public render() {
    return (
      <FormikStageConfig
        {...this.props}
        stage={this.stage}
        onChange={this.props.updateStage}
        validate={validate}
        render={props => (
          <GoogleCloudBuildStageForm {...props} googleCloudBuildAccounts={this.state.googleCloudBuildAccounts} />
        )}
      />
    );
  }
}
