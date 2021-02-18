import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageExecutionLogs, StageFailureMessage } from '../../../details';
export function ApplyEntityTagsExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { current, name, stage } = props;

  const entityRef = stage.context.entityRef || {};
  const tags = stage.context.tags || {};

  const entityRefSection = (
    <div className="row">
      <div className="col-md-12">
        Entity Reference
        <dl className="dl-narrow dl-horizontal">
          {entityRef.cloudProvider && <dt>Provider</dt>}
          {entityRef.cloudProvider && <dd>{entityRef.cloudProvider}</dd>}
          {entityRef.entityType && <dt>Entity Type</dt>}
          {entityRef.entityType && <dd>{entityRef.entityType}</dd>}
          {entityRef.entityId && <dt>Entity ID</dt>}
          {entityRef.entityId && <dd>{entityRef.entityId}</dd>}
          {entityRef.account && <dt>Account</dt>}
          {entityRef.account && <dd>{entityRef.account}</dd>}
          {entityRef.region && <dt>Region</dt>}
          {entityRef.region && <dd>{entityRef.region}</dd>}
          {entityRef.vpcId && <dt>VPC ID</dt>}
          {entityRef.vpcId && <dd>{entityRef.vpcId}</dd>}
        </dl>
      </div>
    </div>
  );

  const tagsSection = (
    <div className="row">
      <div className="col-md-12">
        Tags
        <dl className="dl-narrow dl-horizontal">
          {tags.map(({ name: tagName, value }: any) => {
            if (typeof value === 'object') {
              try {
                value = JSON.stringify(value);
              } catch (ignored) {
                /* noop */
              }
            }

            return (
              <React.Fragment key={tagName}>
                <dt title={tagName}>{tagName}</dt>
                <dd title={value}>{value}</dd>
              </React.Fragment>
            );
          })}
        </dl>
      </div>
    </div>
  );

  return (
    <ExecutionDetailsSection name={name} current={current}>
      {entityRefSection}
      {tagsSection}
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ApplyEntityTagsExecutionDetails {
  export const title = 'applyEntityTagsConfig';
}
