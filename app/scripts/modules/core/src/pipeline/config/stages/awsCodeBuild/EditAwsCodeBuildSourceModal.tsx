import { Form, Formik, FormikProps } from 'formik';
import { get } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import {
  EXCLUDED_ARTIFACT_TYPES,
  IAwsCodeBuildSecondarySourcesVersion,
  IAwsCodeBuildSource,
  SOURCE_TYPES,
} from './IAwsCodeBuildSource';
import {
  FormikFormField,
  FormValidator,
  HelpField,
  IArtifact,
  IExpectedArtifact,
  IFormInputProps,
  IModalComponentProps,
  IPipeline,
  IStage,
  ReactModal,
  ReactSelectInput,
  SpinFormik,
  StageArtifactSelector,
  TextInput,
} from '../../../../index';
import { ModalClose, SubmitButton } from '../../../../modal';

export interface IEditAwsCodeBuildSourceModalProps extends IModalComponentProps {
  source: IAwsCodeBuildSource;
  secondarySourcesVersionOverride: IAwsCodeBuildSecondarySourcesVersion;
  stage: IStage;
  pipeline: IPipeline;
}

export class EditAwsCodeBuildSourceModal extends React.Component<IEditAwsCodeBuildSourceModalProps> {
  private formikRef = React.createRef<Formik<any>>();

  private submit = (values: IAwsCodeBuildSource): void => {
    this.props.closeModal(values);
  };

  public static show(props: any): Promise<IAwsCodeBuildSource> {
    const modalProps = { dialogClassName: 'modal-md' };
    return ReactModal.show(EditAwsCodeBuildSourceModal, props, modalProps);
  }

  private onExpectedArtifactSelected = (
    formik: FormikProps<IAwsCodeBuildSource>,
    artifact: IExpectedArtifact,
  ): void => {
    formik.setFieldValue('sourceArtifact.artifactId', artifact.id);
    formik.setFieldValue('sourceArtifact.artifactDisplayName', artifact.displayName);
    formik.setFieldValue(
      'sourceArtifact.artifactType',
      (artifact.matchArtifact && artifact.matchArtifact.type) ||
        (artifact.defaultArtifact && artifact.defaultArtifact.type),
    );
    formik.setFieldValue('sourceArtifact.artifact', null);
  };

  private onArtifactEdited = (formik: FormikProps<IAwsCodeBuildSource>, artifact: IArtifact): void => {
    formik.setFieldValue('sourceArtifact.artifact', artifact);
    formik.setFieldValue('sourceArtifact.artifactDisplayName', artifact.reference);
    formik.setFieldValue('sourceArtifact.artifactType', artifact.type);
    formik.setFieldValue('sourceArtifact.artifactId', null);
  };

  private validate = (values: IAwsCodeBuildSource): any => {
    const formValidator = new FormValidator(values);
    formValidator
      .field('sourceArtifact', 'Source Artifact') // display name should exist no matter if it's artifact or id
      .required()
      .withValidators((value: any[]) => !value && 'Artifact is required');
    return formValidator.validateForm();
  };

  public render(): React.ReactElement<EditAwsCodeBuildSourceModal> {
    const { dismissModal, source, stage, pipeline } = this.props;
    return (
      <SpinFormik<IAwsCodeBuildSource>
        ref={this.formikRef}
        initialValues={source}
        onSubmit={this.submit}
        validate={this.validate}
        render={(formik) => (
          <Form className={`form-horizontal`}>
            <ModalClose dismiss={dismissModal} />
            <Modal.Header>
              <Modal.Title>Edit Source</Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <div className="form-group">
                <div className="col-md-3 sm-label-right">Source Artifact</div>
                <div className="col-md-9 sm-control-field">
                  <StageArtifactSelector
                    artifact={get(formik, 'values.sourceArtifact.artifact')}
                    excludedArtifactTypePatterns={EXCLUDED_ARTIFACT_TYPES}
                    expectedArtifactId={get(formik, 'values.sourceArtifact.artifactId')}
                    onArtifactEdited={(artifact: IArtifact) => this.onArtifactEdited(formik, artifact)}
                    onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                      this.onExpectedArtifactSelected(formik, artifact)
                    }
                    pipeline={pipeline}
                    stage={stage}
                  />
                </div>
              </div>
              <FormikFormField
                help={<HelpField id="pipeline.config.codebuild.sourceType" />}
                label="Source Type"
                name="type"
                input={(inputProps: IFormInputProps) => (
                  <ReactSelectInput {...inputProps} clearable={true} stringOptions={SOURCE_TYPES} />
                )}
              />
              <FormikFormField
                help={<HelpField id="pipeline.config.codebuild.sourceVersion" />}
                label="Source Version"
                name="sourceVersion"
                input={(inputProps: IFormInputProps) => <TextInput {...inputProps} />}
              />
            </Modal.Body>
            <Modal.Footer>
              <button className="btn btn-default" onClick={dismissModal} type="button">
                Cancel
              </button>
              <SubmitButton isDisabled={!formik.isValid} isFormSubmit={true} submitting={false} label={'Update'} />
            </Modal.Footer>
          </Form>
        )}
      />
    );
  }
}
