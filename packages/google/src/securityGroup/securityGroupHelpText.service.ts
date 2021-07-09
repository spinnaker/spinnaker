import { module } from 'angular';
import { has } from 'lodash';

import { Application, FirewallLabels, IServerGroup } from '@spinnaker/core';

export class GceSecurityGroupHelpTextService {
  private serverGroupsIndexedByTag: Map<string, Set<string>>;
  private application: Application;
  private account: string;
  private network: string;

  public register(application: Application, account: string, network: string): PromiseLike<void> {
    this.reset();
    this.application = application;
    this.account = account;
    this.network = network;
    return this.indexServerGroupsByTag();
  }

  public getHelpTextForTag(tag: string, tagType: 'source' | 'target'): string {
    const serverGroups = this.getServerGroupsWithTag(tag);
    let text: string;
    switch (serverGroups.length) {
      case 0:
        text = null;
        break;
      case 1:
        text = `This ${tagType} tag associates this ${FirewallLabels.get('firewall')} with the server group <em>${
          serverGroups[0]
        }</em>.`;
        break;
      default:
        text = `This ${tagType} tag associates this ${FirewallLabels.get('firewall')} with the server groups
                ${serverGroups.map((serverGroup) => `<em>${serverGroup}</em>`).join(', ')}.`;
        break;
    }
    return text;
  }

  public getServerGroupsWithTag(tag: string): string[] {
    if (this.serverGroupsIndexedByTag.get(tag)) {
      return Array.from(this.serverGroupsIndexedByTag.get(tag)).sort();
    } else {
      return [];
    }
  }

  public reset(): void {
    this.application = null;
    this.account = null;
    this.serverGroupsIndexedByTag = new Map<string, Set<string>>();
  }

  private indexServerGroupsByTag(): PromiseLike<void> {
    return this.application.ready().then(() => {
      this.application.getDataSource('serverGroups').data.forEach((serverGroup: IServerGroup) => {
        if (
          has(serverGroup, 'providerMetadata.tags.length') &&
          serverGroup.account === this.account &&
          serverGroup.providerMetadata?.networkName === this.network
        ) {
          serverGroup.providerMetadata.tags.forEach((tag: string) => {
            if (!this.serverGroupsIndexedByTag.get(tag)) {
              this.serverGroupsIndexedByTag.set(
                tag,
                new Set<string>([serverGroup.name]),
              );
            } else {
              this.serverGroupsIndexedByTag.get(tag).add(serverGroup.name);
            }
          });
        }
      });
    });
  }
}

export const GCE_SECURITY_GROUP_HELP_TEXT_SERVICE = 'spinnaker.gce.securityGroupHelpText.service';
module(GCE_SECURITY_GROUP_HELP_TEXT_SERVICE, []).service(
  'gceSecurityGroupHelpTextService',
  GceSecurityGroupHelpTextService,
);
