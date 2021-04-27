import { isEmpty, template } from 'lodash';
import React from 'react';
import { from as observableFrom, Observable, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { Application } from 'core/application';
import { IManifest } from 'core/domain/IManifest';

import { JobManifestPodLogs } from './JobManifestPodLogs';
import { ManifestReader } from '../ManifestReader';
import { IPodNameProvider } from '../PodNameProvider';

interface IJobStageExecutionLogsProps {
  deployedName: string;
  account: string;
  application: Application;
  externalLink: string;
  podNameProvider: IPodNameProvider;
  location: string;
}

interface IJobStageExecutionLogsState {
  manifest?: IManifest;
}

export class JobStageExecutionLogs extends React.Component<IJobStageExecutionLogsProps, IJobStageExecutionLogsState> {
  public state = {
    manifest: {} as IManifest,
  };

  private destroy$ = new Subject();

  public componentDidMount() {
    const { account, location, deployedName } = this.props;
    observableFrom(ManifestReader.getManifest(account, location, deployedName))
      .pipe(takeUntil(this.destroy$))
      .subscribe(
        (manifest) => this.setState({ manifest }),
        () => {},
      );
  }

  private renderExternalLink(link: string, manifest: IManifest): string {
    if (!link.includes('{{')) {
      return link;
    }
    // use {{ }} syntax to align with the annotation driven UI which this
    // derives from
    return template(link, { interpolate: /{{([\s\S]+?)}}/g })({ ...manifest });
  }

  public render() {
    const { manifest } = this.state;
    const { externalLink, podNameProvider, location, account } = this.props;

    // prefer links to external logging platforms
    if (!isEmpty(manifest) && externalLink) {
      return (
        <a target="_blank" href={this.renderExternalLink(externalLink, manifest)}>
          Console Output (External)
        </a>
      );
    }

    return (
      <>
        {location && (
          <JobManifestPodLogs
            account={account}
            location={location}
            podNameProvider={podNameProvider}
            linkName="Console Output"
          />
        )}
      </>
    );
  }
}
