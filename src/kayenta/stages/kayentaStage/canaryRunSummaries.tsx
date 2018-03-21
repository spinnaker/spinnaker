import * as React from 'react';
import { has } from 'lodash';

import { HoverablePopover, IStage, timestamp, NgReact, ReactInjector } from '@spinnaker/core';
import { CanaryScore } from 'kayenta/components/canaryScore';
import Styleguide from 'kayenta/layout/styleguide';

const { CopyToClipboard } = NgReact;

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

const canaryRunColumns: ICanaryRunColumn[] = [
  {
    label: 'Canary Result',
    width: 2,
    getContent: run => (
      <CanaryScore
        score={run.context.canaryScore}
        health={run.health}
        result={run.result}
        inverse={false}
      />
    ),
  },
  {
    label: 'Duration',
    width: 1,
    getContent: run => <span>{run.context.durationString || ' - '}</span>,
  },
  {
    label: 'Last Updated',
    width: 2,
    getContent: run => <span>{timestamp(run.context.lastUpdated)}</span>,
  },
  {
    width: 1,
    getContent: (run, firstScopeName) => {
      const popoverTemplate = <CanaryRunTimestamps canaryRun={run} firstScopeName={firstScopeName}/>;
      return (
        <section className="horizontal text-center">
          <div className="flex-1">
            <ReportLink canaryRun={run}/>
          </div>
          <div className="flex-1">
            <HoverablePopover template={popoverTemplate}>
              <i className="far fa-clock"/>
            </HoverablePopover>
          </div>
        </section>
      )
    },
  }
];

export default function CanaryRunSummaries({ canaryRuns, firstScopeName }: ICanarySummariesProps) {
  return (
    <Styleguide>
      <section className="canary-run-summaries">
        <CanaryRunHeader/>
        {
          canaryRuns.map(run => (
            <CanaryRunRow canaryRun={run} key={run.id} firstScopeName={firstScopeName}/>
          ))
        }
      </section>
    </Styleguide>
  );
}

function CanaryRunHeader() {
  return (
    <section className="horizontal small grey-border-bottom" style={{ paddingLeft: 0 }}>
      {
        canaryRunColumns.map((column, i) => (
          <div
            className={`flex-${column.width}`}
            key={i}
          >
            <strong>{column.label}</strong>
          </div>
        ))
      }
    </section>
  );
}

function CanaryRunRow({ canaryRun, firstScopeName }: { canaryRun: IStage, firstScopeName: string }) {
  return (
    <section className="horizontal small grey-border-bottom">
      {
        canaryRunColumns.map((column, i) => (
          <div
            className={`flex-${column.width}`}
            key={i}
          >
            {column.getContent(canaryRun, firstScopeName)}
          </div>
        ))
      }
    </section>
  );
}

function CanaryRunTimestamps({ canaryRun, firstScopeName }: { canaryRun: IStage, firstScopeName: string }) {
  const toolTipText = 'Copy timestamp to clipboard';
  return (
    <section className="small">
      <ul className="list-unstyled">
        <li>
          <b>Start:</b>{timestamp(Date.parse(canaryRun.context.scopes[firstScopeName].controlScope.start))}
          <CopyToClipboard text={canaryRun.context.scopes[firstScopeName].controlScope.start} toolTip={toolTipText}/>
        </li>
        <li>
          <b>End:</b> {timestamp(Date.parse(canaryRun.context.scopes[firstScopeName].controlScope.end))}
          <CopyToClipboard text={canaryRun.context.scopes[firstScopeName].controlScope.end} toolTip={toolTipText}/>
        </li>
      </ul>
    </section>
  );
}

function ReportLink({ canaryRun }: { canaryRun: IStage }) {
  if (!has(canaryRun, 'context.canaryConfigId')
      || !has(canaryRun, 'context.canaryPipelineExecutionId')
      || canaryRun.status === 'RUNNING') {
    return null;
  }

  const onClick = () =>
    ReactInjector.$state.go(
      'home.applications.application.canary.report.reportDetail',
      {
        configId: canaryRun.context.canaryConfigId,
        runId: canaryRun.context.canaryPipelineExecutionId,
      },
    );

  return <i className="fa fa-chart-bar clickable" onClick={onClick}/>;
}
