import React from 'react';
import { Modal } from 'react-bootstrap';

import { PlatformHealthOverride } from '../application/modal/PlatformHealthOverride';
import { IConfirmationModalPassthroughProps } from './confirmationModal.service';
import { ModalClose } from '../modal';
import { IModalComponentProps, Markdown, useEscapeKeyPressed } from '../presentation';
import { TaskMonitor, TaskMonitorWrapper, TaskReason, UserVerification } from '../task';
import { MultiTaskMonitor } from '../task/monitor/MultiTaskMonitor';
import { Spinner } from '../widgets/spinners/Spinner';

export interface IConfirmModalProps extends IModalComponentProps, IConfirmationModalPassthroughProps {
  taskMonitor?: TaskMonitor;
  taskMonitors?: TaskMonitor[];
}

export const ConfirmModal = (props: IConfirmModalProps) => {
  const { account, verificationLabel, textToVerify, taskMonitor, taskMonitors, closeModal, dismissModal } = props;
  const { useState, useEffect } = React;

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRetry, setIsRetry] = useState(false);
  const [isValid, setIsValid] = useState(!account && !verificationLabel && !textToVerify);
  const [error, setError] = useState<{ isError: boolean; message?: string }>({ isError: false });

  const [reason, setReason] = useState<string>();
  const [interestingHealthProviderNames, setInterestingHealthProviderNames] = useState<string[]>();

  useEscapeKeyPressed(() => dismissModal());

  useEffect(() => {
    if (taskMonitor && !taskMonitor.modalInstance) {
      taskMonitor.modalInstance = TaskMonitor.modalInstanceEmulation(closeModal, dismissModal);
    }
    if (taskMonitor?.onTaskRetry) {
      const { onTaskRetry } = taskMonitor;
      taskMonitor.onTaskRetry = () => {
        setIsRetry(true);
        setIsSubmitting(false);
        onTaskRetry();
      };
    }
  }, [taskMonitor]);

  const requiresVerification = account || (verificationLabel && textToVerify);
  const isDisabled = isSubmitting || !isValid;

  const showError = (e: string) => {
    setError({ isError: true, message: e });
    setIsSubmitting(false);
  };

  const submit = () => {
    const { submitMethod, submitJustWithReason } = props;
    if (isDisabled) {
      return;
    }
    setIsSubmitting(true);
    if (taskMonitors) {
      taskMonitors.forEach((m) => m.callPreconfiguredSubmit({ reason }));
    } else if (taskMonitor) {
      taskMonitor.submit(() => {
        return submitMethod({
          interestingHealthProviderNames,
          reason,
        });
      });
    } else if (submitJustWithReason) {
      submitMethod({ reason }).then(closeModal, showError);
    } else {
      if (submitMethod) {
        submitMethod().then(closeModal, showError);
      } else {
        closeModal();
      }
    }
  };

  const showReasonInput = ((taskMonitor || taskMonitors) && props.askForReason) || props.submitJustWithReason;
  const showBody =
    (isRetry && props.retryBody) ||
    props.bodyContent ||
    error.isError ||
    props.platformHealthOnlyShowOverride ||
    props.askForReason ||
    props.submitJustWithReason;

  return (
    <div>
      <TaskMonitorWrapper monitor={taskMonitor} />
      <MultiTaskMonitor
        monitors={taskMonitors}
        title={props.multiTaskTitle}
        closeModal={() => dismissModal({ source: 'taskmonitor' })}
      />
      <ModalClose dismiss={() => dismissModal({ source: 'header' })} />
      <Modal.Header>
        <Modal.Title>{props.header}</Modal.Title>
      </Modal.Header>
      {showBody && (
        <Modal.Body>
          {props.bodyContent}
          {props.platformHealthOnlyShowOverride && (
            <PlatformHealthOverride
              interestingHealthProviderNames={interestingHealthProviderNames}
              showHelpDetails={true}
              platformHealthType={props.platformHealthType}
              onChange={setInterestingHealthProviderNames}
            />
          )}
          {error.isError && (
            <div className="alert alert-danger">
              <h4>An error occurred:</h4>
              <p>{error.message || 'No details provided.'}</p>
            </div>
          )}
          {isRetry && props.retryBody && <Markdown message={props.retryBody} />}
          {showReasonInput && <TaskReason reason={reason} onChange={setReason} />}
        </Modal.Body>
      )}
      <Modal.Footer>
        {!isSubmitting && requiresVerification && (
          <UserVerification
            onValidChange={setIsValid}
            account={account}
            expectedValue={textToVerify}
            label={verificationLabel}
          />
        )}
        <button className="btn btn-default" type="button" onClick={() => dismissModal({ source: 'footer' })}>
          {props.cancelButtonText}
        </button>
        <button className="btn btn-primary" type="button" onClick={submit} disabled={isDisabled}>
          {isSubmitting && <Spinner mode="circular" />}
          {props.buttonText}
        </button>
      </Modal.Footer>
    </div>
  );
};
