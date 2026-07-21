import { registerDefaultFixtures } from '../../support';

const awsApplication = {
  name: 'awsapp',
  attributes: {
    name: 'awsapp',
    email: 'aws@example.com',
    cloudProviders: 'aws',
    accounts: 'test',
    instancePort: 80,
    dataSources: {
      disabled: [],
      enabled: [],
    },
  },
  clusters: {},
};

const awsPipelineConfigs = [
  {
    application: 'awsapp',
    id: 'aws-deploy-pipeline',
    index: 0,
    name: 'aws deploy',
    stages: [
      {
        clusters: [],
        name: 'Deploy',
        refId: '1',
        requisiteStageRefIds: [],
        type: 'deploy',
      },
    ],
  },
];

const credentials = [
  {
    accountId: '123456789012',
    accountType: 'aws',
    authorized: true,
    challengeDestructiveActions: false,
    cloudProvider: 'aws',
    defaultKeyPair: 'test-keypair',
    eddaEnabled: false,
    environment: 'test',
    front50Enabled: false,
    name: 'test',
    permissions: {},
    primaryAccount: false,
    regions: [
      {
        availabilityZones: ['us-east-1a', 'us-east-1b'],
        deprecated: false,
        name: 'us-east-1',
        preferredZones: ['us-east-1a', 'us-east-1b'],
      },
    ],
    requiredGroupMembership: [],
    type: 'aws',
  },
];

describe('amazon: Deploy Add Server Group', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications', [awsApplication]);
    cy.intercept('/applications/awsapp?*', awsApplication);
    cy.intercept('/applications/awsapp/pipelines?expand=false', []);
    cy.intercept('/applications/awsapp/pipelines?expand=false&limit=*', []);
    cy.intercept('/applications/awsapp/pipelineConfigs', awsPipelineConfigs);
    cy.intercept('/application/awsapp/pipelineLock', []);
    cy.intercept('/applications/awsapp/serverGroups', []);
    cy.intercept('/applications/awsapp/clusters', []);
    cy.intercept('/applications/awsapp/loadBalancers', []);
    cy.intercept('/executions?limit=*', []);
    cy.intercept('/credentials?expand=true', credentials).as('credentials');
    cy.intercept('/securityGroups', {
      test: {
        aws: {
          'us-east-1': [
            {
              id: 'sg-1234',
              name: 'aws-sg',
              vpcId: 'vpc-1234',
            },
          ],
        },
      },
    }).as('securityGroups');
    cy.intercept('/subnets', [
      {
        account: 'test',
        id: 'subnet-1234',
        purpose: 'internal',
        region: 'us-east-1',
        vpcId: 'vpc-1234',
      },
    ]).as('subnets');
    cy.intercept('/keyPairs', [
      {
        account: 'test',
        keyName: 'test-keypair',
        region: 'us-east-1',
      },
    ]).as('keyPairs');
    cy.intercept('/instanceTypes', [
      {
        account: 'test',
        bareMetal: false,
        burstablePerformanceSupported: true,
        currentGeneration: true,
        defaultVCpus: 1,
        hypervisor: 'nitro',
        instanceStorageSupported: false,
        memoryInGiB: 1,
        name: 't3.micro',
        region: 'us-east-1',
        supportedArchitectures: ['x86_64'],
        supportedRootDeviceTypes: ['ebs'],
        supportedUsageClasses: ['on-demand'],
        supportedVirtualizationTypes: ['hvm'],
      },
    ]).as('instanceTypes');
  });

  it('opens the AWS server group wizard from a deploy stage', () => {
    cy.visit('#/applications/awsapp/executions/configure/aws-deploy-pipeline');

    cy.get('a:contains("Deploy")').click({ force: true });
    cy.get('[data-test-id="Deploy.addServerGroup"]').click();

    cy.get('.modal-title').should('contain.text', 'Configure Deployment Cluster');
    cy.wait(['@credentials', '@securityGroups', '@subnets', '@keyPairs', '@instanceTypes']);
    cy.contains('Loading...').should('not.exist');
    cy.contains('.wizard-modal', 'Basic Settings').should('exist');
    cy.contains('.wizard-modal', 'Instance Type').should('exist');
  });
});
