import { registerDefaultFixtures } from '../../support';

const titusApplication = {
  name: 'titusapp',
  attributes: {
    name: 'titusapp',
    email: 'titus@example.com',
    cloudProviders: 'titus',
    accounts: 'titustestvpc',
    instancePort: 7001,
    dataSources: {
      disabled: [],
      enabled: [],
    },
  },
  clusters: {},
};

const titusPipelineConfigs = [
  {
    application: 'titusapp',
    id: 'titus-deploy-pipeline',
    index: 0,
    name: 'titus deploy',
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
    accountType: 'titus',
    authorized: true,
    awsAccount: 'test',
    awsVpc: 'vpc0',
    challengeDestructiveActions: false,
    cloudProvider: 'titus',
    environment: 'test',
    name: 'titustestvpc',
    permissions: {},
    primaryAccount: false,
    registry: 'registry.example.com',
    regions: [{ name: 'us-east-1' }],
    requiredGroupMembership: [],
    type: 'titus',
  },
  {
    accountId: '123456789012',
    accountType: 'aws',
    authorized: true,
    challengeDestructiveActions: false,
    cloudProvider: 'aws',
    environment: 'test',
    name: 'test',
    permissions: {},
    primaryAccount: false,
    regions: [{ availabilityZones: ['us-east-1a'], name: 'us-east-1' }],
    requiredGroupMembership: [],
    type: 'aws',
  },
];

describe('titus: Deploy Add Server Group', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications', [titusApplication]);
    cy.intercept('/applications/titusapp?*', titusApplication);
    cy.intercept('/applications/titusapp/pipelines?expand=false', []);
    cy.intercept('/applications/titusapp/pipelines?expand=false&limit=*', []);
    cy.intercept('/applications/titusapp/pipelineConfigs', titusPipelineConfigs);
    cy.intercept('/application/titusapp/pipelineLock', []);
    cy.intercept('/applications/titusapp/serverGroups', []);
    cy.intercept('/applications/titusapp/clusters', []);
    cy.intercept('/executions?limit=*', []);
    cy.intercept('/credentials?expand=true', credentials).as('credentials');
    cy.intercept('/securityGroups', {
      test: {
        aws: {
          'us-east-1': [
            {
              id: 'sg-1234',
              name: 'titus-sg',
              vpcId: 'vpc-1234',
            },
          ],
        },
      },
    }).as('securityGroups');
    cy.intercept('/networks/aws', [
      {
        account: 'test',
        cloudProvider: 'aws',
        id: 'vpc-1234',
        name: 'vpc0',
        region: 'us-east-1',
      },
    ]).as('networks');
    cy.intercept('/subnets', [
      {
        account: 'test',
        id: 'subnet-1234',
        purpose: 'titus (vpc0)',
        region: 'us-east-1',
        vpcId: 'vpc-1234',
      },
    ]).as('subnets');
    cy.intercept('/loadBalancers?provider=aws', []).as('loadBalancers');
  });

  it('opens the Titus server group wizard from a deploy stage', () => {
    cy.visit('#/applications/titusapp/executions/configure/titus-deploy-pipeline');

    cy.get('a:contains("Deploy")').click({ force: true });
    cy.get('[data-test-id="Deploy.addServerGroup"]').click();

    cy.get('.modal-title').should('contain.text', 'Configure Deployment Cluster');
    cy.wait(['@credentials', '@securityGroups', '@networks', '@subnets', '@loadBalancers']);
    cy.contains('Loading server group configuration...').should('not.exist');
    cy.contains('.modal-title', 'Configure Deployment Cluster').should('exist');
    cy.contains('.wizard-modal', 'Basic Settings').should('exist');
    cy.contains('.wizard-modal', 'Account').should('exist');
    cy.contains('.wizard-modal', 'Region').should('exist');
  });
});
