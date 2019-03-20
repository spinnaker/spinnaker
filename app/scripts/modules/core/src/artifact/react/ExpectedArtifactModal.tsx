import * as React from 'react';

import { AccountService, IArtifactAccount } from 'core/account';
import { ExpectedArtifactService } from 'core/artifact';
import { IArtifact, IExpectedArtifact, IPipeline } from 'core/domain';
import { HelpField } from 'core/help';
import { WizardModal, WizardPage } from 'core/modal/wizard';
import { IModalComponentProps, ReactModal } from 'core/presentation';
import { FormikFormField } from 'core/presentation/forms';
import { TextInput, CheckboxInput } from 'core/presentation/forms/inputs';
import { TaskMonitor } from 'core/task';
import { noop } from 'core/utils';
import { ArtifactEditor } from './ArtifactEditor';
import { FormikProps } from 'formik';

export interface IExpectedArtifactModalProps extends IModalComponentProps {
  expectedArtifact?: IExpectedArtifact;
  pipeline: IPipeline;
  excludedArtifactTypePatterns?: RegExp[];
  excludedDefaultArtifactTypePatterns?: RegExp[];
}

export interface IExpectedArtifactModalState {
  taskMonitor: TaskMonitor;
  matchArtifactAccounts: IArtifactAccount[];
  defaultArtifactAccounts: IArtifactAccount[];
}

export class ExpectedArtifactModal extends React.Component<IExpectedArtifactModalProps, IExpectedArtifactModalState> {
  constructor(props: IExpectedArtifactModalProps) {
    super(props);

    this.state = {
      matchArtifactAccounts: [],
      defaultArtifactAccounts: [],
      taskMonitor: new TaskMonitor({ title: "I'm never used" }),
    };
  }

  public static defaultProps: Partial<IExpectedArtifactModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public componentDidMount(): void {
    const { excludedArtifactTypePatterns, excludedDefaultArtifactTypePatterns } = this.props;
    AccountService.getArtifactAccounts().then(artifactAccounts => {
      this.setState({
        matchArtifactAccounts: excludedArtifactTypePatterns
          ? artifactAccounts.filter(
              account =>
                !account.types.some(typ => excludedArtifactTypePatterns.some(typPattern => typPattern.test(typ))),
            )
          : artifactAccounts,
        defaultArtifactAccounts: excludedDefaultArtifactTypePatterns
          ? artifactAccounts.filter(
              account =>
                !account.types.some(typ =>
                  excludedDefaultArtifactTypePatterns.some(typPattern => typPattern.test(typ)),
                ),
            )
          : artifactAccounts,
      });
    });
  }

  public static show(props: IExpectedArtifactModalProps): Promise<IExpectedArtifact> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(ExpectedArtifactModal, props, modalProps);
  }

  private editArtifact = (formik: FormikProps<IExpectedArtifact>, field: string, artifact: IArtifact) => {
    formik.setFieldValue(field, artifact);
    formik.setFieldValue(`${field}.artifactAccount`, artifact.artifactAccount);
  };

  public render(): React.ReactNode {
    const { matchArtifactAccounts, defaultArtifactAccounts } = this.state;
    return (
      <WizardModal<IExpectedArtifact>
        heading="Expected Artifact"
        initialValues={this.props.expectedArtifact || ExpectedArtifactService.createEmptyArtifact()}
        taskMonitor={this.state.taskMonitor}
        dismissModal={this.props.dismissModal}
        closeModal={this.props.closeModal}
        submitButtonLabel="Save Artifact"
        render={({ formik, nextIdx, wizard }) => (
          <>
            <WizardPage
              label="General"
              wizard={wizard}
              order={nextIdx()}
              render={() => (
                <FormikFormField
                  name="displayName"
                  label="Display Name"
                  input={props => <TextInput {...props} />}
                  required={true}
                />
              )}
            />
            <WizardPage
              label="Match Artifact"
              wizard={wizard}
              order={nextIdx()}
              render={() => (
                <ArtifactEditor
                  pipeline={this.props.pipeline}
                  artifact={formik.values.matchArtifact}
                  artifactAccounts={matchArtifactAccounts}
                  onArtifactEdit={(artifact: IArtifact) => this.editArtifact(formik, 'matchArtifact', artifact)}
                  isDefault={false}
                />
              )}
            />

            <WizardPage
              label="If Missing"
              wizard={wizard}
              order={nextIdx()}
              render={() => (
                <>
                  <FormikFormField
                    name="usePriorArtifact"
                    label="Use prior execution"
                    fastField={false}
                    input={props => <CheckboxInput {...props} />}
                    help={<HelpField id="pipeline.config.expectedArtifact.usePriorExecution" />}
                  />
                  <FormikFormField
                    name="useDefaultArtifact"
                    label="Use default artifact"
                    fastField={false}
                    input={props => <CheckboxInput {...props} />}
                    help={<HelpField id="pipeline.config.expectedArtifact.defaultArtifact" />}
                  />
                  {formik.values.useDefaultArtifact && (
                    <ArtifactEditor
                      pipeline={this.props.pipeline}
                      artifact={formik.values.defaultArtifact}
                      artifactAccounts={defaultArtifactAccounts}
                      onArtifactEdit={(artifact: IArtifact) => this.editArtifact(formik, 'defaultArtifact', artifact)}
                      isDefault={true}
                    />
                  )}
                </>
              )}
            />
          </>
        )}
      />
    );
  }
}
