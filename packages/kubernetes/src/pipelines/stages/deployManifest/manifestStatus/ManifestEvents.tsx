import { get, trim } from 'lodash';
import { DateTime } from 'luxon';
import React from 'react';

import {
  IManifest,
  IManifestEvent,
  JobEventBasedPodNameProvider,
  JobManifestPodLogs,
  relativeTime,
} from '@spinnaker/core';

export interface IManifestEventsProps {
  manifest: IManifest;
}

export class ManifestEvents extends React.Component<IManifestEventsProps> {
  private pillStyle(e: IManifestEvent): string {
    if (e.type === 'Warning') {
      return 'alert';
    } else if (e.type === 'Normal') {
      return 'success';
    } else {
      return '';
    }
  }

  public render() {
    if (!this.props.manifest) {
      return null;
    }
    if (this.props.manifest && (!this.props.manifest.events || this.props.manifest.events.length === 0)) {
      return <div>No recent events found - Kubernetes does not store events for long.</div>;
    }
    const { events } = this.props.manifest;
    const namespace = trim(
      get(this.props.manifest, ['manifest', 'metadata', 'annotations', 'artifact.spinnaker.io/location'], ''),
    );
    return events.map((e, i) => {
      const firstTimestamp = get(e, 'firstTimestamp', '');
      const lastTimestamp = get(e, 'lastTimestamp', '');
      let firstEpochMilliseconds = 0;
      let lastEpochMilliseconds = 0;
      if (firstTimestamp) {
        firstEpochMilliseconds = DateTime.fromISO(firstTimestamp).toMillis();
      }
      if (lastTimestamp) {
        lastEpochMilliseconds = DateTime.fromISO(lastTimestamp).toMillis();
      }
      return (
        <div key={get(e, ['metadata', 'uid'], String(i))} className="info">
          <div className="horizontal">
            {e.count && (
              <div className={`pill ${this.pillStyle(e)}`}>
                {e.count} × <b>{e.reason}</b>
              </div>
            )}
          </div>
          {(e.firstTimestamp || e.lastTimestamp) && (
            <div>
              {e.firstTimestamp === e.lastTimestamp && (
                <div>
                  <i>{relativeTime(firstEpochMilliseconds)}</i>
                </div>
              )}
              {e.firstTimestamp !== e.lastTimestamp && (
                <div>
                  <div>
                    First Occurrence: <i>{relativeTime(firstEpochMilliseconds)}</i>
                  </div>
                  <div>
                    Last Occurrence: <i>{relativeTime(lastEpochMilliseconds)}</i>
                  </div>
                </div>
              )}
            </div>
          )}
          <div>{e.message}</div>
          <div>
            <JobManifestPodLogs
              account={this.props.manifest.account}
              location={namespace}
              podNamesProviders={[new JobEventBasedPodNameProvider(this.props.manifest, e)]}
              linkName="Console Output (Raw)"
            />
          </div>
          {i !== events.length - 1 && <br />}
        </div>
      );
    });
  }
}
