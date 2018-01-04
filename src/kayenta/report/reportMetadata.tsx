import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import { ICanaryExecutionStatusResult } from '../domain/ICanaryExecutionStatusResult';
import FormattedDate from '../layout/formattedDate';

interface IReportMetadata {
  run: ICanaryExecutionStatusResult;
}

interface IMetadataEntry {
  label: string;
  getContent: () => JSX.Element;
}

const Label = ({ label }: { label: string }) => (
  <label className="label uppercase color-text-primary">
    {label}
  </label>
);

// TODO(dpeach): this only supports canary runs with a single scope.
const buildScopeMetadataEntries = (run: ICanaryExecutionStatusResult): IMetadataEntry[] => {
  const scopes = Object.values(run.result.canaryExecutionRequest.scopes);
  if (scopes.length > 1) {
    return null;
  }

  const {
    controlScope: {
      // If the canary ran through Orca, it's not possible
      // for start, end, and step to be different between control and experiment.
      start,
      end,
      step,
      region: controlRegion,
      scope: controlScope,
    },
    experimentScope: {
      region: experimentRegion,
      scope: experimentScope,
    },
  } = scopes[0];

  return [
    {
      label: 'baseline scope',
      getContent: () => <p>{controlScope}</p>,
    },
    {
      label: 'baseline region',
      getContent: () => <p>{controlRegion}</p>
    },
    {
      label: 'canary scope',
      getContent: () => <p>{experimentScope}</p>
    },
    {
      label: 'canary region',
      getContent: () => <p>{experimentRegion}</p>
    },
    {
      label: 'start',
      getContent: () => <p><FormattedDate dateIso={start}/></p>,
    },
    {
      label: 'end',
      getContent: () => <p><FormattedDate dateIso={end}/></p>
    },
    {
      label: 'step',
      getContent: () => {
        const mins = step / 60;
        return <p>{mins} min{mins === 1 ? '' : 's'}</p>;
      },
    },
  ];
};

const ReportMetadata = ({ run }: IReportMetadata) => {
  const {
    thresholds: {
      marginal,
      pass
    },
  } = run.result.canaryExecutionRequest;

  let metadataEntries: IMetadataEntry[] = [
    {
      label: 'marginal threshold',
      getContent: () => <p>{marginal}</p>
    },
    {
      label: 'pass threshold',
      getContent: () => <p>{pass}</p>,
    },
  ];

  metadataEntries = (buildScopeMetadataEntries(run) || []).concat(metadataEntries);

  return (
    <section className="report-metadata">
      <ul className="list-unstyled list-inline">
        {
          metadataEntries.map(e => (
            <li key={e.label}>
              <Label label={e.label}/>
              {e.getContent()}
            </li>
          ))
        }
      </ul>
    </section>
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  run: state.selectedRun.run,
});

export default connect(mapStateToProps)(ReportMetadata);
