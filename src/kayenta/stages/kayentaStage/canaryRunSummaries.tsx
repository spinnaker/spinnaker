import * as React from 'react';

import { HoverablePopover, IStage, timestamp, NgReact } from '@spinnaker/core';
import { CanaryScore } from 'kayenta/components/canaryScore';
import Styleguide from 'kayenta/layout/styleguide';

const { CopyToClipboard } = NgReact;

import './canaryRunSummaries.less';

interface ICanarySummariesProps {
  canaryRuns: IStage[];
}

export default function CanaryRunSummaries({ canaryRuns }: ICanarySummariesProps) {
  const rows = [
    (
      <CanaryRunRow key="headers">
        <b>Canary Result</b>
        <b>Duration</b>
        <b>Last Updated</b>
      </CanaryRunRow>
    ),
    ...canaryRuns.map(run => <CanaryRunSummary key={run.id} canaryRun={run}/>),
  ];

  return (
    <Styleguide>
      <div className="canary-run-summaries">
        {rows.map((row, i) => <div className="grey-border-bottom" key={i}>{row}</div>)}
      </div>
    </Styleguide>
  );
}

function CanaryRunRow({ children }: { children: JSX.Element[] }) {
  const [result, duration, updated, timestamps] = children;
  return (
    <div className="horizontal small">
      <div className="flex-2">{result}</div>
      <div className="flex-1">{duration}</div>
      <div className="flex-2">{updated}</div>
      <div className="flex-1 text-center">{timestamps}</div>
    </div>
  );
}

function CanaryRunSummary({ canaryRun }: { canaryRun: IStage }) {
  const popoverTemplate = <CanaryRunTimestamps canaryRun={canaryRun}/>;
  return (
    <CanaryRunRow>
      <CanaryScore
        score={canaryRun.context.canaryScore}
        health={canaryRun.health}
        result={canaryRun.result}
        inverse={false}
      />
      <span>{canaryRun.context.durationString || ' - '}</span>
      <span>{timestamp(canaryRun.context.lastUpdated)}</span>
      <HoverablePopover template={popoverTemplate}>
        <i className="fa fa-clock-o"/>
      </HoverablePopover>
    </CanaryRunRow>
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
