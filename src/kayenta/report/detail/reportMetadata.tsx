import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from 'kayenta/reducers';
import { ICanaryExecutionStatusResult } from 'kayenta/domain/ICanaryExecutionStatusResult';
import FormattedDate from 'kayenta/layout/formattedDate';
import SourceLinks from './sourceLinks';
import { HoverablePopover } from '@spinnaker/core';

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
  popover?: boolean;
}

const Label = ({ label, extraClass }: { label: string; extraClass?: string }) => (
  <label className={`label uppercase color-text-primary ${extraClass}`}>{label}</label>
);

// TODO(dpeach): this only supports canary runs with a single scope.
const buildScopeMetadataEntries = (run: ICanaryExecutionStatusResult): IMetadataGroup[] => {
  const request = run.canaryExecutionRequest || run.result.canaryExecutionRequest;
  const scopes = Object.values(request.scopes);
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
      location: controlLocation,
      scope: controlScope,
    },
    experimentScope: { location: experimentLocation, scope: experimentScope },
  } = scopes[0];

  return [
    {
      label: 'baseline',
      entries: [
        {
          label: 'scope',
          popover: true,
          getContent: () => <p className="kayenta-scope">{controlScope}</p>,
        },
        {
          label: 'location',
          getContent: () => <p>{controlLocation}</p>,
        },
      ],
    },
    {
      label: 'canary',
      entries: [
        {
          label: 'scope',
          popover: true,
          getContent: () => <p className="kayenta-scope">{experimentScope}</p>,
        },
        {
          label: 'location',
          getContent: () => <p>{experimentLocation}</p>,
        },
      ],
    },
    {
      label: 'time',
      entries: [
        {
          label: 'start',
          getContent: () => (
            <p>
              <FormattedDate dateIso={start} />
            </p>
          ),
        },
        {
          label: 'end',
          getContent: () => (
            <p>
              <FormattedDate dateIso={end} />
            </p>
          ),
        },
        {
          label: 'step',
          getContent: () => {
            const mins = step / 60;
            return (
              <p>
                {mins} min
                {mins === 1 ? '' : 's'}
              </p>
            );
          },
        },
      ],
    },
  ];
};

const ReportMetadata = ({ run }: IReportMetadata) => {
  const {
    thresholds: { marginal, pass },
  } = run.canaryExecutionRequest || run.result.canaryExecutionRequest;

  const metadataGroups = buildScopeMetadataEntries(run) || [];
  metadataGroups.push({
    label: 'threshold',
    entries: [
      {
        label: 'marginal',
        getContent: () => <p>{marginal}</p>,
      },
      {
        label: 'pass',
        getContent: () => <p>{pass}</p>,
      },
    ],
  });

  return (
    <section className="report-metadata">
      {metadataGroups.map((group, index) => (
        <div className={`group group-${group.label}`} key={group.label || index}>
          <Label label={group.label || ''} extraClass="label-lg" />
          <ul className="list-unstyled">
            {group.entries.map(e => (
              <li key={e.label || index}>
                <Label label={e.label} />
                {e.popover ? (
                  <HoverablePopover template={e.getContent()}>{e.getContent()}</HoverablePopover>
                ) : (
                  <>{e.getContent()}</>
                )}
              </li>
            ))}
          </ul>
        </div>
      ))}
      <div className="group group-source" key="source">
        <Label label="Source" extraClass="label-lgf" />
        <SourceLinks />
      </div>
    </section>
  );
};

const mapStateToProps = (state: ICanaryState) => ({
  run: state.selectedRun.run,
});

export default connect(mapStateToProps)(ReportMetadata);
