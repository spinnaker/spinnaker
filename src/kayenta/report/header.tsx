import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import {
  ICanaryJudgeGroupScore,
  ICanaryJudgeScore
} from '../domain/ICanaryJudgeResult';
import CanaryJudgeScore from './score';
import GroupScores from './groupScores';
import * as Creators from 'kayenta/actions/creators';

interface IReportDetailHeaderStateProps {
  groups: ICanaryJudgeGroupScore[];
  score: ICanaryJudgeScore;
}

interface IReportDetailDispatchProps {
  clearSelectedGroup: () => void;
}

/*
* Layout for the report detail header.
* */
const ReportDetailHeader = ({ groups, score, clearSelectedGroup }: IReportDetailHeaderStateProps & IReportDetailDispatchProps) => (
  <section className="horizontal container">
    <CanaryJudgeScore score={score} onClick={clearSelectedGroup} className="flex-1"/>
    <GroupScores groups={groups} className="flex-6"/>
  </section>
);

const mapStateToProps = (state: ICanaryState): IReportDetailHeaderStateProps => ({
  groups: state.selectedReport.report.groupScores,
  score: state.selectedReport.report.score,
});

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IReportDetailDispatchProps => ({
  clearSelectedGroup: () => dispatch(Creators.selectReportGroup({ group: null })),
});

export default connect(mapStateToProps, mapDispatchToProps)(ReportDetailHeader);
