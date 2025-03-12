import { CanarySettings } from 'kayenta/canary.settings';
import {
  CANARY_EXECUTION_NO_PIPELINE_STATUS,
  ICanaryExecutionStatusResult,
  ICanaryMetricConfig,
  ICanaryScopesByName,
  IKayentaAccount,
} from 'kayenta/domain';
import CenteredDetail from 'kayenta/layout/centeredDetail';
import FormattedDate from 'kayenta/layout/formattedDate';
import { ITableColumn, NativeTable } from 'kayenta/layout/table';
import { ManualAnalysisModal } from 'kayenta/manualAnalysis/ManualAnalysisModal';
import { ICanaryState } from 'kayenta/reducers';
import { get, isEqual } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';

import { Application, ReactInjector, relativeTime, Spinner, Tooltip } from '@spinnaker/core';

import ConfigLink from './configLink';
import Score from '../detail/score';
import { PipelineLink } from './pipelineLink';
import ReportLink from './reportLink';

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

const baseColumns: Array<ITableColumn<ICanaryExecutionStatusResult>> = [
  {
    label: 'Summary',
    getContent: (execution) => (
      <div>
        <ReportLink
          configName={execution.config.name}
          executionId={execution.pipelineId}
          application={execution.application}
        >
          <Score score={execution.result.judgeResult.score} showClassification={false} inverse={true} />
          {'  '}
          <FormattedDate dateIso={execution.startTimeIso} />
        </ReportLink>
        {execution.startTimeIso && (
          <div className="color-text-caption body-small">
            {relativeTime(new Date(execution.startTimeIso).getTime())}
          </div>
        )}
      </div>
    ),
  },
  {
    label: 'Locations',
    getContent: ({ canaryExecutionRequest: { scopes }, config: { metrics } }) => {
      const locations = [...getScopeLocations(scopes, metrics)];

      return (
        <div className="vertical">
          {locations.map((location) => (
            <span key={location}>{location}</span>
          ))}
        </div>
      );
    },
  },
  {
    label: 'Config',
    getContent: (execution) => (
      <ConfigLink
        configName={execution.config.name}
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

      const styles: React.CSSProperties = { maxWidth: '350px', wordBreak: 'break-all' };
      if (areScopesIdentical) {
        return (
          <div className="vertical" style={styles}>
            {[...canaryScopeNames].map((scope) => (
              <span key={scope}>{scope}</span>
            ))}
          </div>
        );
      } else {
        return (
          <div className="vertical" style={styles}>
            <span className="heading-6 uppercase color-text-caption">Baseline</span>
            {[...baselineScopeNames].map((scope) => (
              <span key={scope}>{scope}</span>
            ))}
            <span className="heading-6 uppercase color-text-caption" style={{ marginTop: '5px' }}>
              Canary
            </span>
            {[...canaryScopeNames].map((scope) => (
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

const getTableColumns = (application: Application, accounts: IKayentaAccount[]) => {
  if (!CanarySettings.manualAnalysisEnabled) {
    return baseColumns;
  }
  return baseColumns.concat([
    {
      label: 'Actions',
      getContent: (execution) => (
        <Tooltip value="Re-run analysis">
          <button className="link" onClick={() => rerunAnalysis(execution, application, accounts)}>
            <i className="fas fa-redo-alt" />
          </button>
        </Tooltip>
      ),
    },
  ]);
};

const rerunAnalysis = (
  execution: ICanaryExecutionStatusResult,
  application: Application,
  accounts: IKayentaAccount[],
) => {
  const { controlScope, experimentScope } = Object.values(execution.canaryExecutionRequest.scopes)[0];
  const step = `${controlScope.step ?? 60}`;
  ManualAnalysisModal.show({
    title: 'Start Manual Analysis',
    application,
    accounts,
    initialValues: {
      configId: execution.canaryConfigId,
      startTime: controlScope.start,
      endTime: controlScope.end,
      step,
      baselineScope: controlScope.scope,
      canaryScope: experimentScope.scope,
      baselineLocation: controlScope.location,
      canaryLocation: experimentScope.location,
      extendedScopeParams: controlScope.extendedScopeParams,
      resourceType: controlScope.resourceType,
      marginalThreshold: `${execution.canaryExecutionRequest.thresholds.marginal}`,
      passThreshold: `${execution.canaryExecutionRequest.thresholds.pass}`,
      metricsAccountName: execution.metricsAccountName,
      storageAccountName: execution.storageAccountName,
    },
  });
};

const startManualAnalysis = (application: Application, accounts: IKayentaAccount[]) => {
  ManualAnalysisModal.show({
    title: 'Start Manual Analysis',
    application,
    accounts,
  });
};

interface IExecutionListTableStateProps {
  executions: ICanaryExecutionStatusResult[];
  application: Application;
  accounts: IKayentaAccount[];
  executionsCount: number;
}

const TableRows = ({
  executions,
  application,
  accounts,
}: {
  executions: ICanaryExecutionStatusResult[];
  application: Application;
  accounts: IKayentaAccount[];
}) => {
  if (!application.getDataSource('canaryExecutions').loaded) {
    return (
      <CenteredDetail>
        <div className="horizontal center middle spinner-container">
          <Spinner />
        </div>
      </CenteredDetail>
    );
  }
  if (!executions || !executions.length) {
    return (
      <CenteredDetail>
        <h3 className="heading-3">No canary execution history for this application.</h3>
      </CenteredDetail>
    );
  }
  return (
    <NativeTable
      rows={executions}
      className="flex-1 execution-list-table"
      columns={getTableColumns(application, accounts)}
      rowKey={(execution) => execution.pipelineId}
    />
  );
};

const updateExecutionsCount = (event: React.ChangeEvent<HTMLSelectElement>) => {
  ReactInjector.$state.go('.', { count: Number(event.target.value) });
};

const ExecutionListTable = ({ executions, application, accounts, executionsCount }: IExecutionListTableStateProps) => {
  React.useEffect(() => {
    const dataSource = application.getDataSource('canaryExecutions');
    if (!dataSource.active) {
      dataSource.activate();
    }
    dataSource.refresh();
    return () => dataSource.deactivate();
  }, [application, executionsCount]);

  const countOptions: number[] = CanarySettings.executionsCountOptions ?? [20, 50, 100, 200];
  if (!countOptions.includes(executionsCount)) {
    countOptions.push(executionsCount);
    countOptions.sort((a, b) => a - b);
  }

  return (
    <div className="vertical execution-list-container">
      <div className="horizontal space-between form-inline">
        <div className="form-group">
          {countOptions.length > 1 && (
            <>
              <span className="sp-margin-s-right">Showing</span>
              <select className="form-control input-sm" onChange={updateExecutionsCount}>
                {countOptions.map((o) => (
                  <option selected={executionsCount === o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
              <span className="sp-margin-s-left">most recent reports</span>
            </>
          )}
        </div>
        {CanarySettings.manualAnalysisEnabled && (
          <button
            style={{ alignSelf: 'flex-end', flexShrink: 0 }}
            className="primary"
            onClick={() => startManualAnalysis(application, accounts)}
          >
            <i className="fa fa-play" /> Start Manual Analysis
          </button>
        )}
      </div>
      <TableRows executions={executions} application={application} accounts={accounts} />
    </div>
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  executions: Object.values(state.data.executions.data).filter((e) => e.result),
  application: state.data.application,
  accounts: state.data.kayentaAccounts.data,
  executionsCount: state.app.executionsCount,
});

export default connect(mapStateToProps)(ExecutionListTable);
