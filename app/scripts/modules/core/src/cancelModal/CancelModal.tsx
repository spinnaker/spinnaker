import { Field, FieldProps, Form, Formik } from 'formik';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { ModalClose, SubmitButton } from '../modal';
import { Markdown, ReactModal } from '../presentation';

export interface ICancelModalProps {
  body?: string;
  buttonText: string;
  cancelButtonText?: string;
  header: string;
  submitMethod: (reason: string, force?: boolean) => PromiseLike<any>;
  closeModal?(result?: any): void; // provided by ReactModal
  dismissModal?(rejection?: any): void; // provided by ReactModal
}

export interface ICancelModalState {
  isSubmitting: boolean;
  error: boolean;
  errorMessage?: string;
}

export interface ICancelModalValues {
  reason?: string;
}

export class CancelModal extends React.Component<ICancelModalProps, ICancelModalState> {
  public static defaultProps: Partial<ICancelModalProps> = {
    cancelButtonText: 'Cancel',
  };

  public state: ICancelModalState = { isSubmitting: false, error: false };

  public static confirm(props: ICancelModalProps): Promise<void> {
    return ReactModal.show(CancelModal, props);
  }

  private close = (args?: any): void => {
    this.props.dismissModal.apply(null, args);
  };

  private showError = (exception: string): void => {
    this.setState({
      error: true,
      errorMessage: exception,
      isSubmitting: false,
    });
  };

  private submitConfirmation = (values: ICancelModalValues): void => {
    this.setState({ isSubmitting: true });
    this.props.submitMethod(values.reason).then(this.close, this.showError);
  };

  public render() {
    const { header, body, buttonText, cancelButtonText } = this.props;
    const { isSubmitting } = this.state;

    const error = this.state.error ? (
      <div>
        <div className="alert alert-danger">
          <h4>An exception occurred:</h4>
          <p>{this.state.errorMessage || 'No details provided.'}</p>
        </div>
      </div>
    ) : null;

    return (
      <Formik
        initialValues={{}}
        onSubmit={this.submitConfirmation}
        render={() => {
          const wrappedBody = body ? <Markdown message={body} trim={true} /> : null;

          return (
            <Form className="form-horizontal">
              <ModalClose dismiss={this.close} />
              <Modal.Header>
                <Modal.Title>{header}</Modal.Title>
              </Modal.Header>
              <Modal.Body>
                {wrappedBody}
                {error}
                <div
                  className="row"
                  style={{
                    marginTop: '10px',
                    marginBottom: '10px',
                  }}
                >
                  <div className="col-md-3 sm-label-right">Reason</div>
                  <div className="col-md-7">
                    <Field
                      name="reason"
                      render={({ field }: FieldProps<ICancelModalValues>) => (
                        <textarea
                          className="form-control"
                          {...field}
                          rows={3}
                          placeholder="(Optional) anything that might be helpful to explain the reason for this change; HTML is okay"
                        />
                      )}
                    />
                  </div>
                </div>
              </Modal.Body>
              <Modal.Footer>
                <button className="btn btn-default" disabled={isSubmitting} onClick={this.close} type="button">
                  {cancelButtonText}
                </button>
                <SubmitButton
                  isDisabled={isSubmitting}
                  submitting={isSubmitting}
                  isFormSubmit={true}
                  label={buttonText}
                />
              </Modal.Footer>
            </Form>
          );
        }}
      />
    );
  }
}
