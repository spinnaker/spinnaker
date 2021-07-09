import React, { useState } from 'react';

import { Application, IExecution, IExecutionStage, Spinner } from '@spinnaker/core';
import { AwsReactInjector } from '../../../reactShims';

export interface IEvaluateCloudFormationChangeSetExecutionApprovalProps {
  execution: IExecution;
  stage: IExecutionStage;
  application: Application;
}

export interface IEvaluateCloudFormationChangeSetExecutionApprovalState {
  submitting: boolean;
  judgmentDecision: string;
  error: boolean;
}

export const EvaluateCloudFormationChangeSetExecutionApproval = (
  props: IEvaluateCloudFormationChangeSetExecutionApprovalProps,
) => {
  const { execution, stage, application } = props;
  const [submitting, setSubmitting] = useState(false);
  const [judgmentDecision, setJudgmentDecision] = useState('');
  const [error, setError] = useState(false);

  const provideJudgment = (judgmentDecision: string) => {
    setSubmitting(true);
    setJudgmentDecision(judgmentDecision);
    setError(false);
    AwsReactInjector.evaluateCloudFormationChangeSetExecutionService.evaluateExecution(
      application,
      execution,
      stage,
      judgmentDecision,
    );
  };

  const isSubmitting = (decision: string): boolean => {
    return stage.judgmentStatus === decision || (submitting && judgmentDecision === decision);
  };

  const handleContinueClick = (): void => {
    provideJudgment('skip');
  };

  const handleFailClick = (): void => {
    provideJudgment('fail');
  };

  const handleExecuteClick = (): void => {
    provideJudgment('execute');
  };

  return (
    <div>
      <div>
        <p>
          This ChangeSet contains a replacement, which means there will be <b>potential data loss</b> when executed.
        </p>
        <p>How do you want to proceed?</p>
        <div className="action-buttons">
          <button className="btn btn-danger" onClick={handleExecuteClick} disabled={submitting}>
            {isSubmitting('Execute') && <Spinner mode="circular" />}
            {stage.context.stopButtonLabel || 'Execute'}
          </button>
          <button className="btn btn-primary" disabled={submitting} onClick={handleContinueClick}>
            {isSubmitting('Skip') && <Spinner mode="circular" />}
            {stage.context.skipButtonLabel || 'Skip'}
          </button>
          <button className="btn btn-primary" disabled={submitting} onClick={handleFailClick}>
            {isSubmitting('Fail') && <Spinner mode="circular" />}
            {stage.context.FailButtonLabel || 'Fail'}
          </button>
        </div>
      </div>
      {error && <div className="error-message">There was an error recording your decision. Please try again.</div>}
    </div>
  );
};
