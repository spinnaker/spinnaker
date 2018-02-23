import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from 'kayenta/reducers';
import { ICanaryExecutionStatusResult } from 'kayenta/domain/ICanaryExecutionStatusResult';
import FormattedDate from 'kayenta/layout/formattedDate';
import SourceLinks from './sourceLinks';

interface IReportMetadata {
  run: ICanaryExecutionStatusResult;
}

interface IMetadataGroup {
  label?: string;
  entries: IMetadataEntry[];
}

interface IMetadataEntry {
  label: string;
  getContent: () => JSX.Element;
}

const Label = ({ label, extraClass }: { label: string, extraClass?: string }) => (
  <label className={`label uppercase color-text-primary ${extraClass}`}>
    {label}
  </label>
);

// TODO(dpeach): this only supports canary runs with a single scope.
const buildScopeMetadataEntries = (run: ICanaryExecutionStatusResult): IMetadataGroup[] => {
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
      label: 'baseline',
      entries: [
        {
          label: 'scope',
          getContent: () => <p>{controlScope}</p>,
        },
        {
          label: 'region',
          getContent: () => <p>{controlRegion}</p>
        },
      ]
    },
    {
      label: 'canary',
      entries: [
        {
          label: 'scope',
          getContent: () => <p>{experimentScope}</p>
        },
        {
          label: 'region',
          getContent: () => <p>{experimentRegion}</p>
        },
      ]
    },
    {
      label: 'time',
      entries: [
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
      ]
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

  const metadataGroups = (buildScopeMetadataEntries(run) || []);
  metadataGroups.push({
    label: 'threshold',
    entries: [
      {
        label: 'marginal',
        getContent: () => <p>{marginal}</p>
      },
      {
        label: 'pass',
        getContent: () => <p>{pass}</p>,
      }
    ]
  });

  return (
    <section className="report-metadata">
      <div className="horizontal space-between bottom">
        {
          metadataGroups.map((group, index) => (
            <div key={group.label || index}>
              <Label label={group.label || ''} extraClass="label-lg"/>
              <ul className="list-unstyled list-inline">
                {
                  group.entries.map(e => (
                    <li key={e.label || index}>
                      <Label label={e.label}/>
                      {e.getContent()}
                    </li>
                  ))
                }
              </ul>
            </div>
          ))
        }
        <div key="source">
          <Label label="Source" extraClass="label-lgf"/>
          <SourceLinks/>
        </div>
      </div>
    </section>
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  run: state.selectedRun.run,
});

export default connect(mapStateToProps)(ReportMetadata);
