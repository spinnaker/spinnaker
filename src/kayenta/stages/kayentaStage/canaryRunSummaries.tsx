import * as React from 'react';
import { get, has } from 'lodash';

import { HoverablePopover, CopyToClipboard, IStage, timestamp, ReactInjector } from '@spinnaker/core';
import { CanaryScore } from 'kayenta/components/canaryScore';
import Styleguide from 'kayenta/layout/styleguide';
import { ITableColumn, NativeTable } from 'kayenta/layout/table';

import './canaryRunSummaries.less';

export interface ICanarySummariesProps {
  canaryRuns: IStage[];
  firstScopeName: string;
}

export interface ICanaryRunColumn {
  label?: string;
  width: number;
  getContent: (run: IStage, firstScopeName?: string) => JSX.Element;
}

export default function CanaryRunSummaries({ canaryRuns, firstScopeName }: ICanarySummariesProps) {
  const canaryRunColumns: Array<ITableColumn<IStage>> = [
    {
      label: 'Canary Result',
      getContent: run => {
        return (
          <>
            <CanaryScore
              score={run.context.canaryScore}
              health={run.health}
              result={run.result}
              inverse={false}
              className="label"
            />
            {get(run, ['context', 'warnings'], []).length > 0 && (
              <HoverablePopover template={<CanaryRunWarningMessages messages={run.context.warnings} />}>
                <i className="fa fa-exclamation-triangle" style={{ paddingLeft: '8px' }} />
              </HoverablePopover>
            )}
          </>
        );
      },
    },
    {
      label: 'Duration',
      getContent: run => <span>{run.context.durationString || ' - '}</span>,
    },
    {
      label: 'Last Updated',
      getContent: run => <span>{timestamp(run.context.lastUpdated)}</span>,
    },
    {
      getContent: run => {
        const popoverTemplate = <CanaryRunTimestamps canaryRun={run} firstScopeName={firstScopeName} />;
        return (
          <section className="horizontal text-center">
            <div className="flex-1">
              <ReportLink canaryRun={run} />
            </div>
            <div className="flex-1">
              <HoverablePopover template={popoverTemplate}>
                <i className="far fa-clock" />
              </HoverablePopover>
            </div>
          </section>
        );
      },
    },
  ];

  return (
    <Styleguide className="horizontal flex-1">
      <NativeTable
        rows={canaryRuns}
        className="header-transparent flex-1"
        columns={canaryRunColumns}
        rowKey={run => run.id}
      />
    </Styleguide>
  );
}

function CanaryRunTimestamps({ canaryRun, firstScopeName }: { canaryRun: IStage; firstScopeName: string }) {
  const toolTipText = 'Copy timestamp to clipboard';
  return (
    <section className="small">
      <ul className="list-unstyled">
        <li>
          <b>Start:</b> {timestamp(Date.parse(canaryRun.context.scopes[firstScopeName].experimentScope.start))}
          <CopyToClipboard
            displayText={false}
            text={canaryRun.context.scopes[firstScopeName].experimentScope.start}
            toolTip={toolTipText}
          />
        </li>
        <li>
          <b>End:</b> {timestamp(Date.parse(canaryRun.context.scopes[firstScopeName].experimentScope.end))}
          <CopyToClipboard
            displayText={false}
            text={canaryRun.context.scopes[firstScopeName].experimentScope.end}
            toolTip={toolTipText}
          />
        </li>
      </ul>
    </section>
  );
}

function ReportLink({ canaryRun }: { canaryRun: IStage }) {
  if (
    !has(canaryRun, 'context.canaryConfigId') ||
    !has(canaryRun, 'context.canaryPipelineExecutionId') ||
    canaryRun.status === 'RUNNING'
  ) {
    return null;
  }

  const onClick = () =>
    ReactInjector.$state.go('home.applications.application.canary.report.reportDetail', {
      configId: canaryRun.context.canaryConfigId,
      runId: canaryRun.context.canaryPipelineExecutionId,
    });

  return <i className="fa fa-chart-bar clickable" onClick={onClick} />;
}

function CanaryRunWarningMessages({ messages }: { messages: string[] }) {
  return (
    <div>
      {messages.map((message, i) => (
        <p key={`${i}-${message}`}>{message}</p>
      ))}
    </div>
  );
}
