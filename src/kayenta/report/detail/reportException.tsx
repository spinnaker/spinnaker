import { ICanaryExecutionException } from 'kayenta/domain';
import { ICanaryState } from 'kayenta/reducers';
import * as React from 'react';
import { connect } from 'react-redux';

import SourceLinks from './sourceLinks';

import './reportExplanation.less';

interface IReportExceptionProps {
  exception: ICanaryExecutionException;
}

const ReportException = ({ exception }: IReportExceptionProps) => {
  const errorMessages = [];
  if (exception.details) {
    // error messages can be in an array on the details, or a single field, or possibly both?
    // it's unclear based on Orca where the error message might be
    const { error, errors } = exception.details;
    if (error) {
      errorMessages.push(error);
    }
    if (errors?.length) {
      errors.forEach((m: string) => errorMessages.push(m));
    }
  }
  // if there were no errors, set a default message
  if (!errorMessages.length) {
    errorMessages.push('No error message provided. Click the "Report" link to see more details.');
  }

  return (
    <>
      <h3 className="text-center">Canary report failed</h3>
      <dl>
        <dt>Details</dt>
        <dd>
          {errorMessages.map((message, i) => (
            <div key={i}>
              <code>{message}</code>
            </div>
          ))}
        </dd>
        <dt>Source</dt>
        <dd>
          <SourceLinks />
        </dd>
      </dl>
    </>
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  exception: state.selectedRun.run.exception,
});

export default connect(mapStateToProps)(ReportException);
