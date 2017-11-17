import * as React from 'react';
import { connect } from 'react-redux';
import { UISref } from '@uirouter/react';

import { ICanaryState } from '../reducers/index';
import { serializedCanaryConfigSelector } from 'kayenta/selectors';

export interface IReportHeaderStateProps {
  id: string;
  name: string
}

const ReportHeader = ({ id, name }: IReportHeaderStateProps) => (
  <section>
    <h1 className="heading-1 color-text-primary">
      Report:
      <UISref to="^.^.canaryConfig.configDetail" params={{ id }}>
        <a className="clickable color-text-primary"> {name}</a>
      </UISref>
    </h1>
  </section>
);

const mapStateToProps = (state: ICanaryState): IReportHeaderStateProps => ({
  id: serializedCanaryConfigSelector(state).id,
  name: serializedCanaryConfigSelector(state).name,
});

export default connect(mapStateToProps)(ReportHeader);
