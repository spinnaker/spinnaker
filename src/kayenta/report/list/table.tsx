import * as React from 'react';
import { connect } from 'react-redux';
import * as moment from 'moment';
import { isEqual, get } from 'lodash';

import { ITableColumn, NativeTable } from 'kayenta/layout/table';
import { ICanaryState } from 'kayenta/reducers';
import {
  ICanaryExecutionStatusResult,
  ICanaryMetricConfig,
  ICanaryScopesByName,
  CANARY_EXECUTION_NO_PIPELINE_STATUS,
} from 'kayenta/domain';
import FormattedDate from 'kayenta/layout/formattedDate';
import CenteredDetail from 'kayenta/layout/centeredDetail';
import Score from '../detail/score';
import ReportLink from './reportLink';
import ConfigLink from './configLink';
import { PipelineLink } from './pipelineLink';

import './executionList.less';

// Both this and the atlas-specific logic in `getScopeLocations` shouldn't really
// be in this file, and probably shouldn't even be referenced directly.
// Ideally this and any other metric store customizations should happen in a registry
// of store-specific transformation functions or similar.
const isAtlasScope = (scope: string, metrics: ICanaryMetricConfig[]) =>
  metrics.some(({ query, scopeName }) => scopeName === scope && query.type === 'atlas');

const getScopeLocations = (scopes: ICanaryScopesByName, metrics: ICanaryMetricConfig[]) =>
  Object.keys(scopes).reduce((acc, scopeName) => {
    const { controlScope, experimentScope } = scopes[scopeName];
    const isAtlas = isAtlasScope(scopeName, metrics);

    // When atlas metrics have the dataset param set to global,
    // the location field is not accurate.
    if (isAtlas && get(controlScope, 'extendedScopeParams.dataset') === 'global') {
      acc.add('Global');
    } else {
      acc.add(controlScope.location);
    }
    if (isAtlas && get(experimentScope, 'extendedScopeParams.dataset') === 'global') {
      acc.add('Global');
    } else {
      acc.add(experimentScope.location);
    }

    return acc;
  }, new Set<string>());

const columns: ITableColumn<ICanaryExecutionStatusResult>[] = [
  {
    label: 'Summary',
    getContent: execution => (
      <>
        <ReportLink
          configName={execution.config ? execution.config.name : execution.result.config.name}
          executionId={execution.pipelineId}
          application={execution.application}
        >
          <Score score={execution.result.judgeResult.score} showClassification={false} inverse={true} />
          {'  '}
          <FormattedDate dateIso={execution.startTimeIso} />
        </ReportLink>
        {'  '}
        {execution.startTimeIso && (
          <span className="color-text-caption body-small" style={{ marginLeft: '10px' }}>
            {moment(execution.startTimeIso).fromNow()}
          </span>
        )}
      </>
    ),
  },
  {
    label: 'Locations',
    getContent: ({ canaryExecutionRequest: { scopes }, config: { metrics } }) => {
      const locations = [...getScopeLocations(scopes, metrics)];

      return (
        <div className="vertical">
          {locations.map(location => (
            <span key={location}>{location}</span>
          ))}
        </div>
      );
    },
  },
  {
    label: 'Config',
    getContent: execution => (
      <ConfigLink
        configName={execution.config ? execution.config.name : execution.result.config.name}
        executionId={execution.pipelineId}
        application={execution.application}
      />
    ),
  },
  {
    label: 'Scopes',
    getContent: ({ canaryExecutionRequest: { scopes } }) => {
      const baselineScopeNames = Object.keys(scopes).reduce(
        (acc, scope) => acc.add(scopes[scope].controlScope.scope),
        new Set<string>(),
      );
      const canaryScopeNames = Object.keys(scopes).reduce(
        (acc, scope) => acc.add(scopes[scope].experimentScope.scope),
        new Set<string>(),
      );

      const areScopesIdentical = isEqual(baselineScopeNames, canaryScopeNames);

      if (areScopesIdentical) {
        return (
          <div className="vertical">
            {[...canaryScopeNames].map(scope => (
              <span key={scope}>{scope}</span>
            ))}
          </div>
        );
      } else {
        return (
          <div className="vertical">
            <span className="heading-6 uppercase color-text-caption">Baseline</span>
            {[...baselineScopeNames].map(scope => (
              <span key={scope}>{scope}</span>
            ))}
            <span className="heading-6 uppercase color-text-caption" style={{ marginTop: '5px' }}>
              Canary
            </span>
            {[...canaryScopeNames].map(scope => (
              <span key={scope}>{scope}</span>
            ))}
          </div>
        );
      }
    },
  },
  {
    getContent: ({ parentPipelineExecutionId, application }) =>
      parentPipelineExecutionId &&
      parentPipelineExecutionId !== CANARY_EXECUTION_NO_PIPELINE_STATUS && (
        <PipelineLink parentPipelineExecutionId={parentPipelineExecutionId} application={application} />
      ),
  },
];

interface IExecutionListTableStateProps {
  executions: ICanaryExecutionStatusResult[];
}

const ExecutionListTable = ({ executions }: IExecutionListTableStateProps) => {
  if (!executions || !executions.length) {
    return (
      <CenteredDetail>
        <h3 className="heading-3">No canary execution history for this application.</h3>
      </CenteredDetail>
    );
  }

  return (
    <div className="vertical execution-list-container">
      <NativeTable
        rows={executions}
        className="flex-1 execution-list-table"
        columns={columns}
        rowKey={execution => execution.pipelineId}
      />
    </div>
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  executions: Object.values(state.data.executions.data).filter(e => e.result),
});

export default connect(mapStateToProps)(ExecutionListTable);
