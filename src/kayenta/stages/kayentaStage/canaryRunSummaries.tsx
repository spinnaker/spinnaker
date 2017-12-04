import * as React from 'react';
import { has } from 'lodash';

import { HoverablePopover, IStage, timestamp, NgReact, ReactInjector } from '@spinnaker/core';
import { CanaryScore } from 'kayenta/components/canaryScore';
import Styleguide from 'kayenta/layout/styleguide';

const { CopyToClipboard } = NgReact;

import './canaryRunSummaries.less';

export interface ICanarySummariesProps {
  canaryRuns: IStage[];
}

export interface ICanaryRunColumn {
  label?: string;
  width: number;
  getContent: (run: IStage) => JSX.Element;
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
    getContent: run => {
      const popoverTemplate = <CanaryRunTimestamps canaryRun={run}/>;
      return (
        <section className="horizontal text-center">
          <div className="flex-1">
            <ReportLink canaryRun={run}/>
          </div>
          <div className="flex-1">
            <HoverablePopover template={popoverTemplate}>
              <i className="fa fa-clock-o"/>
            </HoverablePopover>
          </div>
        </section>
      )
    },
  }
];

export default function CanaryRunSummaries({ canaryRuns }: ICanarySummariesProps) {
  return (
    <Styleguide>
      <section className="canary-run-summaries">
        <CanaryRunHeader/>
        {
          canaryRuns.map(run => (
            <CanaryRunRow canaryRun={run} key={run.id}/>
          ))
        }
      </section>
    </Styleguide>
  );
}

function CanaryRunHeader() {
  return (
    <section className="horizontal small grey-border-bottom" style={{paddingLeft: 0}}>
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

function CanaryRunRow({ canaryRun }: { canaryRun: IStage }) {
  return (
    <section className="horizontal small grey-border-bottom">
      {
        canaryRunColumns.map((column, i) => (
          <div
            className={`flex-${column.width}`}
            key={i}
          >
            {column.getContent(canaryRun)}
          </div>
        ))
      }
    </section>
  );
}

function CanaryRunTimestamps({ canaryRun }: { canaryRun: IStage }) {
  const toolTipText = 'Copy timestamp to clipboard';
  return (
    <section className="small">
      <ul className="list-unstyled">
        <li>
          <b>Start:</b> {timestamp(Date.parse(canaryRun.context.startTimeIso))}
          <CopyToClipboard text={canaryRun.context.startTimeIso} toolTip={toolTipText}/>
        </li>
        <li>
          <b>End:</b> {timestamp(Date.parse(canaryRun.context.endTimeIso))}
          <CopyToClipboard text={canaryRun.context.endTimeIso} toolTip={toolTipText}/>
        </li>
      </ul>
    </section>
  );
}

function ReportLink({ canaryRun }: { canaryRun: IStage }) {
  if (!has(canaryRun, 'context.canaryConfigId')
      || !has(canaryRun, 'context.canaryPipelineExecutionId')) {
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

  return <i className="fa fa-bar-chart clickable" onClick={onClick}/>;
}
