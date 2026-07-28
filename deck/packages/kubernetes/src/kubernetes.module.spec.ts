import { CloudProviderRegistry } from '@spinnaker/core';

import { KubernetesInstanceDetails } from './instance';
import { registerKubernetesProvider } from './kubernetes.module';
import { ManifestWizard } from './manifest/wizard/ManifestWizard';
import { KubernetesSecurityGroupDetails, KubernetesV2SecurityGroupTransformer } from './securityGroup';

describe('Kubernetes provider registration', () => {
  it('registers the provider configuration', () => {
    registerKubernetesProvider();

    expect(CloudProviderRegistry.getValue('kubernetes', 'instance.details').displayName).toBe(
      KubernetesInstanceDetails.displayName,
    );
    expect(CloudProviderRegistry.getValue('kubernetes', 'securityGroup.CreateSecurityGroupModal')).toBe(ManifestWizard);
    expect(CloudProviderRegistry.getValue('kubernetes', 'securityGroup.details').displayName).toBe(
      KubernetesSecurityGroupDetails.displayName,
    );
    expect(CloudProviderRegistry.getValue('kubernetes', 'securityGroup.transformer')).toBe(
      KubernetesV2SecurityGroupTransformer,
    );
    expect(CloudProviderRegistry.getValue('kubernetes', 'instance.detailsController')).toBeNull();
    expect(CloudProviderRegistry.getValue('kubernetes', 'securityGroup.detailsController')).toBeNull();
  });
});
