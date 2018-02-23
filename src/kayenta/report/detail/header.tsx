import * as React from 'react';
import { connect } from 'react-redux';
import { UISref } from '@uirouter/react';

import { ICanaryState } from 'kayenta/reducers';
import { judgeResultSelector, serializedCanaryConfigSelector } from 'kayenta/selectors';
import { ICanaryJudgeScore } from 'kayenta/domain/ICanaryJudgeResult';
import ReportMetadata from './reportMetadata';
import ReportScore from './score';

import './header.less';

export interface IReportHeaderStateProps {
  id: string;
  name: string;
  score: ICanaryJudgeScore;
}

const ReportHeader = ({ id, name, score }: IReportHeaderStateProps) => (
  <section className="horizontal report-header">
    <h1 className="heading-1 color-text-primary">
      <UISref to="^.^.canaryConfig.configDetail" params={{ id }}>
        <a className="clickable color-text-primary"> {name}</a>
      </UISref>
    </h1>
    <div className="report-score-wrapper">
      <ReportScore score={score} showClassification={true}/>
    </div>
    <ReportMetadata/>
  </section>
);

const mapStateToProps = (state: ICanaryState): IReportHeaderStateProps => ({
  id: serializedCanaryConfigSelector(state).id,
  name: serializedCanaryConfigSelector(state).name,
  score: judgeResultSelector(state).score,
});

export default connect(mapStateToProps)(ReportHeader);
