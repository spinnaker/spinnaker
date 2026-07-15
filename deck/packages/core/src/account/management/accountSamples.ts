import type { IAccountDefinition } from './AccountManagementService';

export interface IAccountSample {
  /** Short description shown next to the type selector. */
  description: string;
  /** Sample definition used to seed the JSON editor. */
  sample: IAccountDefinition;
}

/**
 * Starter definitions for the account types storable via the account
 * management APIs (clouddriver CredentialsDefinition classes annotated with
 * @JsonTypeName). Field names mirror the corresponding properties classes in
 * clouddriver; values are placeholders to be replaced.
 */
export const ACCOUNT_SAMPLES: Record<string, IAccountSample> = {
  kubernetes: {
    description: 'Deploys to a Kubernetes cluster using a kubeconfig context.',
    sample: {
      type: 'kubernetes',
      name: 'my-kubernetes-account',
      context: 'my-cluster-context',
      kubeconfigFile: '/path/to/kubeconfig',
      namespaces: [],
      onlySpinnakerManaged: true,
      cacheThreads: 1,
      permissions: {
        READ: ['my-team-role'],
        WRITE: ['my-team-role'],
      },
    },
  },
  aws: {
    description: 'Manages EC2 resources in an AWS account, typically via an assumed IAM role.',
    sample: {
      type: 'aws',
      name: 'my-aws-account',
      accountId: '123456789012',
      assumeRole: 'role/spinnakerManaged',
      regions: [{ name: 'us-east-1' }, { name: 'us-west-2' }],
      environment: 'production',
      accountType: 'production',
      permissions: {
        READ: ['my-team-role'],
        WRITE: ['my-team-role'],
      },
    },
  },
  ecs: {
    description: 'Deploys to Amazon ECS; references an existing AWS account by name.',
    sample: {
      type: 'ecs',
      name: 'my-ecs-account',
      awsAccount: 'my-aws-account',
    },
  },
  google: {
    description: 'Manages Google Compute Engine resources in a GCP project.',
    sample: {
      type: 'google',
      name: 'my-google-account',
      project: 'my-gcp-project',
      jsonPath: '/path/to/service-account.json',
      regions: ['us-central1', 'us-east1'],
      permissions: {
        READ: ['my-team-role'],
        WRITE: ['my-team-role'],
      },
    },
  },
  azure: {
    description: 'Manages Azure resources in a subscription via a service principal.',
    sample: {
      type: 'azure',
      name: 'my-azure-account',
      clientId: '00000000-0000-0000-0000-000000000000',
      appKey: 'encrypted:secrets-manager!<parameters>',
      tenantId: '00000000-0000-0000-0000-000000000000',
      subscriptionId: '00000000-0000-0000-0000-000000000000',
      defaultResourceGroup: 'my-resource-group',
      defaultKeyVault: 'my-key-vault',
      regions: ['eastus', 'westus'],
      permissions: {
        READ: ['my-team-role'],
        WRITE: ['my-team-role'],
      },
    },
  },
  dockerRegistry: {
    description: 'Indexes a Docker registry for images used as pipeline triggers and bake targets.',
    sample: {
      type: 'dockerRegistry',
      name: 'my-docker-registry',
      address: 'https://index.docker.io',
      username: 'my-user',
      password: 'encrypted:secrets-manager!<parameters>',
      repositories: ['library/nginx'],
    },
  },
};

/** Builds the JSON editor seed for the given type/name, using a sample when one exists. */
export function buildSampleDefinition(type: string, name: string): string {
  const sample = ACCOUNT_SAMPLES[type]?.sample;
  const definition = sample ? { ...sample, type, name } : { type, name };
  return JSON.stringify(definition, null, 2);
}
