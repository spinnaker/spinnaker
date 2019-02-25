import * as React from 'react';

import { get } from 'lodash';
import { UISref } from '@uirouter/react';
import { DateTime } from 'luxon';
import { IManifest, Spinner, CloudProviderLogo, CollapsibleSection, AccountTag, timestamp } from '@spinnaker/core';
import { KubernetesManifestService } from '../manifest/manifest.service';
import { ManifestEvents } from 'kubernetes/v2/pipelines/stages/deployManifest/react/ManifestEvents';
import { ManifestLabels } from 'kubernetes/v2/manifest/ManifestLabels';
import { ManifestCondition } from 'kubernetes/v2/manifest/status/ManifestCondition';

export interface IKubernetesResourceDetailsProps {
  app: any;
  kubernetesResource: any;
}

export interface IKubernetesResourceDetailsState {
  manifest?: IManifest;
  loading: boolean;
}

export class KubernetesResourceDetails extends React.Component<
  IKubernetesResourceDetailsProps,
  IKubernetesResourceDetailsState
> {
  private unsubscribeManifest?: () => void;

  constructor(props: IKubernetesResourceDetailsProps) {
    super(props);
    this.state = {
      loading: true,
      manifest: null,
    };
  }

  public componentDidMount() {
    const { kubernetesResource } = this.props;
    const params = {
      account: kubernetesResource.accountId,
      location: kubernetesResource.region,
      name: kubernetesResource.kubernetesResource,
    };
    this.unsubscribeManifest = KubernetesManifestService.subscribe(this.props.app, params, manifest => {
      if (this.unsubscribeManifest != null) {
        this.setState({ manifest, loading: false });
      }
    });
  }

  public componentWillUnmount() {
    if (this.unsubscribeManifest) {
      this.unsubscribeManifest();
      this.unsubscribeManifest = null;
    }
  }

  public render() {
    const { manifest } = this.state;
    const metadata = get(manifest, ['manifest', 'metadata'], null);
    const creationUnixMs =
      get(metadata, 'creationTimestamp') && DateTime.fromISO(metadata.creationTimestamp).toMillis();
    return (
      <div className="details-panel">
        <div className="header">
          <div className="close-button">
            <a className="btn btn-link">
              <UISref to="^">
                <span className="glyphicon glyphicon-remove" />
              </UISref>
            </a>
          </div>
          {this.state.loading ? (
            <div className="horizontal center middle">
              <Spinner size="small" />
            </div>
          ) : (
            <div className="header-text horizontal middle">
              <CloudProviderLogo provider="kubernetes" height="36px" width="36px" />
              <h3 className="horizontal middle space-between flex-1">{get(metadata, ['name'], '')}</h3>
            </div>
          )}
        </div>
        {!this.state.loading && (
          <div className="content">
            <CollapsibleSection heading="Information">
              <dl className="dl-horizontal dl-flex">
                <dt>Created</dt>
                <dd>{timestamp(creationUnixMs)}</dd>
                <dt>Account</dt>
                <dd>
                  <AccountTag account={get(manifest, ['account'], '')} />
                </dd>
                <dt>Namespace</dt>
                <dd>{get(metadata, ['namespace'], '')}</dd>
                <dt>Kind</dt>
                <dd>{get(manifest, ['manifest', 'kind'], '')}</dd>
              </dl>
            </CollapsibleSection>
            <CollapsibleSection key="status" heading="status" defaultExpanded={true}>
              <ul>
                {get(manifest, ['manifest', 'status', 'conditions'], []).map(condition => (
                  <li key={condition.type + condition.lastTransitionTime} style={{ marginBottom: '10px' }}>
                    <ManifestCondition condition={condition} />
                  </li>
                ))}
              </ul>
            </CollapsibleSection>
            <CollapsibleSection key="events" heading="events" defaultExpanded={true}>
              <ManifestEvents manifest={manifest} />
            </CollapsibleSection>
            <CollapsibleSection key="labels" heading="labels" defaultExpanded={true}>
              <ManifestLabels manifest={get(manifest, ['manifest'], {})} />
            </CollapsibleSection>
          </div>
        )}
      </div>
    );
  }
}
