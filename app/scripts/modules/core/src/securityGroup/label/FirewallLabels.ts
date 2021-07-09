import { SETTINGS } from '../../config/settings';

export class FirewallLabels {
  private static labels: Map<string, string> = new Map<string, string>();

  /**
   * Returns the overridden label, or the label if no override is registered
   */
  public static get(label: string): string {
    return this.labels.get(label) || label;
  }

  /**
   * Adds a label to the registry.
   */
  public static register(label: string, translation: string): void {
    this.labels.set(label, translation);
  }
}

if (SETTINGS.useClassicFirewallLabels) {
  FirewallLabels.register('firewall', 'security group');
  FirewallLabels.register('firewalls', 'security groups');
  FirewallLabels.register('Firewall', 'Security Group');
  FirewallLabels.register('Firewalls', 'Security Groups');
}
