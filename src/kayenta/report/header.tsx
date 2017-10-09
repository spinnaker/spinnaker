import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import {
  ICanaryJudgeGroupScore,
  ICanaryJudgeScore
} from '../domain/ICanaryJudgeResult';
import CanaryJudgeScore from './score';
import GroupScores from './groupScores';

interface IReportDetailHeaderStateProps {
  groups: ICanaryJudgeGroupScore[];
  score: ICanaryJudgeScore;
}

/*
* Layout for the report detail header.
* */
const ReportDetailHeader = ({ groups, score }: IReportDetailHeaderStateProps) => (
  <section className="horizontal container">
    <CanaryJudgeScore score={score} className="flex-1"/>
    <GroupScores groups={groups} className="flex-6"/>
  </section>
);

const mapStateToProps = (state: ICanaryState): IReportDetailHeaderStateProps => ({
  groups: state.selectedReport.report.groupScores,
  score: state.selectedReport.report.score,
});

export default connect(mapStateToProps)(ReportDetailHeader);
