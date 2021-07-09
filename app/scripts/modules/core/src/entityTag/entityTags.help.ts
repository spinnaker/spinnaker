import { HelpContentsRegistry } from '../help/helpContents.registry';

const helpContents: any[] = [
  {
    key: 'entityTags.serverGroup.alert',
    contents: `<p>Alerts indicate an issue with a server group. When present, an alert icon
      <i class="notification fa fa-exclamation-triangle"></i> will be displayed in the clusters view next to the server group.</p>`,
  },
  {
    key: 'entityTags.serverGroup.notice',
    contents: `<p>Notices provide additional context for a server group. When present, an info icon
      <i class="notification fa fa-flag"></i> will be displayed in the clusters view next to the server group.</p>`,
  },
  {
    key: 'entityTags.loadBalancer.alert',
    contents: `<p>Alerts indicate an issue with a load balancer. When present, an alert icon
      <i class="notification fa fa-exclamation-triangle"></i> will be displayed in the load balancers view next to the server group.</p>`,
  },
  {
    key: 'entityTags.loadBalancer.notice',
    contents: `<p>Notices provide additional context for a load balancer. When present, an info icon
      <i class="notification fa fa-flag"></i> will be displayed in the load balancers view next to the server group.</p>`,
  },
  {
    key: 'entityTags.securityGroup.notice',
    contents: `<p>Notices provide additional context for a {{firewall}}. When present, an info icon
      <i class="notification fa fa-flag"></i> will be displayed in the {{firewalls}} view next to the {{firewall}}.</p>`,
  },
  {
    key: 'entityTags.securityGroup.alert',
    contents: `<p>Alerts indicate an issue with a {{firewall}}. When present, an alert icon
      <i class="notification fa fa-exclamation-triangle"></i> will be displayed in the {{firewall}} view next to the {{firewall}}.</p>`,
  },
];

helpContents.forEach((entry: any) => HelpContentsRegistry.register(entry.key, entry.contents));
