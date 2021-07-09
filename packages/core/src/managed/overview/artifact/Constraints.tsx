import classnames from 'classnames';
import { isEmpty } from 'lodash';
import React from 'react';

import { RelativeTimestamp } from '../../RelativeTimestamp';
import { VersionOperationIcon } from './VersionOperation';
import { constraintsManager } from '../../constraints/registry';
import { FetchVersionDocument, useUpdateConstraintMutation } from '../../graphql/graphql-sdk';
import { CollapsibleSection, useApplicationContextSafe } from '../../../presentation';
import { ArtifactVersionProps, QueryConstraint } from '../types';
import { getConstraintsStatusSummary } from './utils';
import { useLogEvent } from '../../utils/logging';
import { NotifierService, Spinner } from '../../../widgets';

import './Constraints.less';

interface IConstraintContentProps {
  constraint: QueryConstraint;
  versionProps: ArtifactVersionProps;
}

const ConstraintContent = ({ constraint, versionProps }: IConstraintContentProps) => {
  const description = constraintsManager.renderDescription(constraint);
  const actions = constraintsManager.getActions(constraint)?.sort((action) => (action.pass ? -1 : 1)); // positive actions first
  const application = useApplicationContextSafe();
  const logEvent = useLogEvent('ArtifactConstraints', 'UpdateStatus');

  const [updateConstraint, { loading, error }] = useUpdateConstraintMutation({
    refetchQueries: [
      { query: FetchVersionDocument, variables: { appName: application?.name, versions: [versionProps.version] } },
    ],
  });

  React.useEffect(() => {
    if (error) {
      NotifierService.publish({
        action: 'create',
        key: 'updateConstraintError',
        content: `Failed to update constraint - ${error.message}`,
        options: { type: 'error' },
      });
    }
  }, [error]);

  return (
    <dl className="constraint-content">
      {description && <dd>{description}</dd>}
      {!isEmpty(actions) && (
        <dd className={classnames(description ? 'sp-margin-s-top' : undefined, 'horizontal middle')}>
          {actions?.map(({ title, pass }) => (
            <button
              className={classnames('btn md-btn constraint-action-button', pass ? 'md-btn-success' : 'md-btn-danger')}
              key={title}
              disabled={loading}
              onClick={() => {
                logEvent({ data: { newStatus: pass } });
                updateConstraint({
                  variables: {
                    payload: {
                      application: application.name,
                      environment: versionProps.environment,
                      version: versionProps.version,
                      type: constraint.type,
                      reference: versionProps.reference,
                      status: pass ? 'FORCE_PASS' : 'FAIL',
                    },
                  },
                });
              }}
            >
              {title}
            </button>
          ))}
          {loading && <Spinner mode="circular" size="nano" color="var(--color-accent)" />}
        </dd>
      )}
    </dl>
  );
};

interface IConstraintProps {
  constraint: QueryConstraint;
  versionProps: ArtifactVersionProps;
}

const Constraint = ({ constraint, versionProps }: IConstraintProps) => {
  const hasContent = constraintsManager.hasContent(constraint);
  const title = constraintsManager.renderTitle(constraint);
  return (
    <div className="version-constraint single-constraint">
      <VersionOperationIcon status={constraint.status} />
      <CollapsibleSection
        outerDivClassName=""
        defaultExpanded
        toggleClassName="constraint-toggle"
        enableCaching={false}
        expandIconSize="12px"
        expandIconPosition="right"
        heading={({ chevron }) => (
          <div className="constraint-title">
            <div>
              {title}
              {constraint.judgedAt && (
                <span className="sp-margin-xs-left">
                  (<RelativeTimestamp timestamp={constraint.judgedAt} withSuffix />)
                </span>
              )}
            </div>
            {chevron}
          </div>
        )}
      >
        {hasContent ? <ConstraintContent constraint={constraint} versionProps={versionProps} /> : undefined}
      </CollapsibleSection>
    </div>
  );
};

export const Constraints = ({
  constraints,
  versionProps,
  expandedByDefault,
}: {
  constraints?: QueryConstraint[];
  versionProps: ArtifactVersionProps;
  expandedByDefault?: boolean;
}) => {
  if (!constraints || !constraints.length) return null;
  const summary = getConstraintsStatusSummary(constraints);
  return (
    <div className="Constraints">
      <div className="version-constraint">
        <VersionOperationIcon status={summary.status} />
        <CollapsibleSection
          heading={({ chevron }) => (
            <div className="horizontal">
              Constraints: {summary.text} {chevron}
            </div>
          )}
          outerDivClassName=""
          toggleClassName="constraint-toggle"
          bodyClassName="sp-margin-xs-top sp-margin-xs-bottom"
          expandIconSize="12px"
          expandIconPosition="right"
          defaultExpanded={expandedByDefault}
          enableCaching={false}
        >
          {constraints?.map((constraint, index) => (
            <Constraint key={index} constraint={constraint} versionProps={versionProps} />
          ))}
        </CollapsibleSection>
      </div>
    </div>
  );
};
