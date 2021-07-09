import React from 'react';

import { Button } from '../Button';
import { useLogEvent } from './logging';
import {
  FormikFormField,
  IModalComponentProps,
  ModalBody,
  ModalFooter,
  ModalHeader,
  SpinFormik,
  TextAreaInput,
  ValidationMessage,
} from '../../presentation';

export interface IArtifactActionModalProps extends IModalComponentProps {
  title: string;
  actionName: string;
  withComment?: boolean;
  logCategory?: string;
  onAction: (comment?: string) => Promise<void> | PromiseLike<void>;
  onSuccess?: () => void;
  error?: string;
}

export const ActionModal: React.FC<IArtifactActionModalProps> = ({
  title,
  dismissModal,
  closeModal,
  onAction,
  onSuccess,
  actionName,
  withComment = true,
  logCategory,
  children,
}) => {
  const logEvent = useLogEvent(logCategory || 'ActionModal', actionName);
  return (
    <>
      <ModalHeader className="truncate">{title}</ModalHeader>
      <SpinFormik<{ comment?: string }>
        initialValues={{}}
        onSubmit={async ({ comment }, { setSubmitting, setStatus }) => {
          if (withComment && !comment) return;
          logEvent();
          try {
            await onAction(comment);
            onSuccess?.();
            closeModal?.();
          } catch (error) {
            setStatus({ error });
          } finally {
            setSubmitting(false);
          }
        }}
        render={({ status, isValid, isSubmitting, submitForm }) => {
          return (
            <>
              <ModalBody>
                <div className="flex-container-v middle sp-padding-xl-yaxis">
                  {children}
                  {withComment && (
                    <FormikFormField
                      label="Reason"
                      name="comment"
                      required={true}
                      input={(props) => (
                        <TextAreaInput
                          {...props}
                          rows={5}
                          required={true}
                          placeholder="Please provide a reason. Markdown is supported :)"
                        />
                      )}
                    />
                  )}
                  {status?.error && (
                    <div className="sp-margin-xl-top">
                      <ValidationMessage
                        type="error"
                        message={
                          <span className="flex-container-v">
                            <span className="text-bold">Something went wrong:</span>
                            {status.error.message && <span>{status.error.message}</span>}
                          </span>
                        }
                      />
                    </div>
                  )}
                </div>
              </ModalBody>
              <ModalFooter
                primaryActions={
                  <div className="flex-container-h sp-group-margin-s-xaxis">
                    <Button onClick={() => dismissModal?.()}>Cancel</Button>
                    <Button appearance="primary" disabled={!isValid || isSubmitting} onClick={() => submitForm()}>
                      {actionName}
                    </Button>
                  </div>
                }
              />
            </>
          );
        }}
      />
    </>
  );
};
