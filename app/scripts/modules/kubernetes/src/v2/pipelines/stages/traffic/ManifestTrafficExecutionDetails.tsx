import * as React from 'react';
import { get } from 'lodash';

import {
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageFailureMessage,
  StageConstants,
} from '@spinnaker/core';

interface IManifestTrafficExecutionDetailsProps extends IExecutionDetailsSectionProps {
  context: {
    manifestName: string;
    location: string;
    cluster: string;
    criteria: string;
  };
}

export const manifestTrafficExecutionDetails = (stageName: string) => {
  return class ManifestTrafficExecutionDetails extends React.Component<IManifestTrafficExecutionDetailsProps> {
    public static title = stageName;

    public mapCriteriaToLabel = (criteria: string): string =>
      get(StageConstants.MANIFEST_CRITERIA_OPTIONS.find(option => option.val === criteria), 'label');

    public render() {
      const { stage, current, name } = this.props;
      return (
        <ExecutionDetailsSection name={name} current={current}>
          <div className="row">
            <div className="col-md-9">
              <dl className="dl-narrow dl-horizontal">
                <dt>Account</dt>
                <dd>{stage.context.account}</dd>
                {stage.context.manifestName != null && (
                  <>
                    <dt>Manifest</dt>
                    <dd>{stage.context.manifestName}</dd>
                  </>
                )}
                <dt>Namespace</dt>
                <dd>{stage.context.location}</dd>
                {this.mapCriteriaToLabel(stage.context.criteria) != null &&
                  stage.context.cluster != null && (
                    <>
                      <dt>Target</dt>
                      <dd>{`${this.mapCriteriaToLabel(stage.context.criteria)} in cluster ${
                        stage.context.cluster
                      }`}</dd>
                    </>
                  )}
              </dl>
            </div>
          </div>
          <StageFailureMessage stage={stage} message={stage.failureMessage} />
        </ExecutionDetailsSection>
      );
    }
  };
};
