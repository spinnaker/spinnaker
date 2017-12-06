import * as React from 'react';
import { connect } from 'react-redux';
import { UISref } from '@uirouter/react';

import { ICanaryState } from '../reducers/index';
import { serializedCanaryConfigSelector } from 'kayenta/selectors';
import ReportMetadata from './reportMetadata';

export interface IReportHeaderStateProps {
  id: string;
  name: string
}

const ReportHeader = ({ id, name }: IReportHeaderStateProps) => (
  <section className="horizontal">
    <h1
      className="heading-1 color-text-primary"
      style={{
        paddingRight: '20px',
        borderRight: '1px solid var(--color-titanium)',
      }}
    >
      Report:
      <UISref to="^.^.canaryConfig.configDetail" params={{ id }}>
        <a className="clickable color-text-primary"> {name}</a>
      </UISref>
    </h1>
    <ReportMetadata/>
  </section>
);

const mapStateToProps = (state: ICanaryState): IReportHeaderStateProps => ({
  id: serializedCanaryConfigSelector(state).id,
  name: serializedCanaryConfigSelector(state).name,
});

export default connect(mapStateToProps)(ReportHeader);
