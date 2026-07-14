import { registerDefaultFixtures } from '../../support';

const pipelineId = 'gce-load-balancer-pipeline';
const networkPipelineId = 'gce-network-load-balancer-pipeline';

const loadBalancerTypes = [
  {
    choice: 'Network',
    heading: 'Configure Network Load Balancer',
    name: 'gce-network',
    pipelineId: networkPipelineId,
    type: 'NETWORK',
    expected: {
      healthCheck: {
        checkIntervalSec: 10,
        healthyThreshold: 10,
        port: 80,
        requestPath: '/',
        timeoutSec: 5,
        unhealthyThreshold: 2,
      },
      ipProtocol: 'TCP',
      portRange: '8080',
      region: 'us-central1',
      sessionAffinity: 'NONE',
    },
  },
  {
    choice: 'Internal',
    heading: 'Create INTERNAL Load Balancer',
    name: 'gce-internal',
    type: 'INTERNAL',
    configure: () => {
      configureProxy('gce-internal');
      setSelectField('Region', 'us-central1');
      setSelectField('Network', 'default');
      setSelectField('Subnet', 'lb-subnet');
      setTextField('Listener ports', '80,443');
    },
    expected: {
      backendService: {
        healthCheck: { healthCheckType: 'TCP', name: 'gce-internal-hc', port: 80 },
        name: 'gce-internal',
        sessionAffinity: 'NONE',
      },
      ipProtocol: 'TCP',
      network: 'default',
      ports: ['80', '443'],
      region: 'us-central1',
      subnet: 'lb-subnet',
    },
  },
  {
    choice: 'TCP',
    heading: 'Create TCP Load Balancer',
    name: 'gce-tcp',
    type: 'TCP',
    configure: () => configureProxy('gce-tcp'),
    expected: {
      backendService: {
        healthCheck: { healthCheckType: 'TCP', name: 'gce-tcp-hc', port: 80 },
        name: 'gce-tcp',
        portName: 'http',
        sessionAffinity: 'NONE',
      },
      ipProtocol: 'TCP',
      portRange: '443',
      region: 'global',
    },
  },
  {
    choice: 'SSL',
    heading: 'Create SSL Load Balancer',
    name: 'gce-ssl',
    type: 'SSL',
    configure: () => {
      configureProxy('gce-ssl');
      setSelectField('Certificate', 'lb-cert');
    },
    expected: {
      backendService: {
        healthCheck: { healthCheckType: 'TCP', name: 'gce-ssl-hc', port: 80 },
        name: 'gce-ssl',
        portName: 'http',
        sessionAffinity: 'NONE',
      },
      certificate: 'lb-cert',
      ipProtocol: 'TCP',
      portRange: '443',
      region: 'global',
    },
  },
  {
    choice: 'HTTP(S)',
    heading: 'Create HTTP(S) load balancer',
    name: 'gce-http-listener',
    type: 'HTTP',
    configure: () => configureHttp('gce-http', 'gce-http-listener'),
    expected: {
      certificate: null,
      defaultService: {
        account: 'gce',
        healthCheck: {
          account: 'gce',
          checkIntervalSec: 10,
          healthCheckType: 'HTTP',
          healthyThreshold: 2,
          name: 'lb-health-check',
          port: 80,
          requestPath: '/',
          timeoutSec: 5,
          unhealthyThreshold: 2,
        },
        name: 'lb-backend',
        portName: 'http',
        region: 'global',
        sessionAffinity: 'NONE',
      },
      hostRules: [],
      portRange: '80',
      region: 'global',
      urlMapName: 'gce-http',
    },
  },
  {
    choice: 'Internal HTTP(S)',
    heading: 'Create Internal managed HTTP(S) load balancer',
    name: 'gce-internal-http-listener',
    type: 'INTERNAL_MANAGED',
    configure: () => {
      configureHttp('gce-internal-http', 'gce-internal-http-listener', false);
      cy.get('[data-testid="region"]').select('us-central1');
      cy.get('[data-testid="network"]').select('default');
      cy.get('[data-testid="subnet"]').select('lb-subnet');
      cy.contains('button', 'Add health check').click();
      cy.get('[data-testid="health-check-name"]').clear().type('lb-health-check');
      cy.contains('button', 'Add backend service').click();
      cy.get('[data-testid="backend-service-name"]').clear().type('lb-backend');
      cy.get('[data-testid="backend-service-health-check"]').select('lb-health-check');
      cy.get('[data-testid="default-backend-service"]').select('lb-backend');
    },
    expected: {
      certificate: null,
      defaultService: {
        healthCheck: {
          checkIntervalSec: 10,
          healthCheckType: 'HTTP',
          healthyThreshold: 10,
          name: 'lb-health-check',
          port: 80,
          requestPath: '/',
          timeoutSec: 5,
          unhealthyThreshold: 2,
        },
        name: 'lb-backend',
        portName: 'http',
        sessionAffinity: 'NONE',
      },
      hostRules: [],
      network: 'default',
      portRange: '80',
      region: 'us-central1',
      subnet: 'lb-subnet',
      urlMapName: 'gce-internal-http',
    },
  },
];

describe('google: Load Balancers', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('**/auth/user', { fixture: 'default/auth.user.anonymous.json' });
    cy.fixture('google/load_balancers/resources.json').then((resources) => {
      cy.intercept('/credentials?expand=true', resources.credentials);
      cy.intercept('/networks/gce', resources.networks);
      cy.intercept('/subnets/gce', resources.subnets);
      Object.entries(resources.search).forEach(([type, response]) => {
        cy.intercept(`/search?*type=${type}*`, response).as(type);
      });
    });
    cy.intercept('/applications/compute/loadBalancers', []);
    cy.intercept('/applications/compute/pipelineConfigs', {
      fixture: 'google/load_balancers/pipelineConfigs.json',
    });
    cy.intercept('/applications/compute/pipelines?*', []);
    cy.intercept('/tasks/gce-load-balancer-task', {
      application: 'compute',
      id: 'gce-load-balancer-task',
      name: 'Create load balancer',
      status: 'SUCCEEDED',
      variables: [],
    });
  });

  loadBalancerTypes.forEach(({ choice, configure, expected, heading, name, pipelineId: typePipelineId, type }) => {
    it(`routes and submits a ${type} load balancer`, () => {
      if (typePipelineId) {
        cy.intercept('POST', '/pipelines?staleCheck=true', (request) => {
          const command = request.body.stages[0].loadBalancers[0];
          assertCommand(command, { expected, name, type });
          request.reply({});
        }).as('savePipeline');

        cy.visit(`#/applications/compute/executions/configure/${typePipelineId}`);
        cy.contains('.pipeline-config-graph .label-body.node', 'Create Load Balancers').click({ force: true });
        cy.contains('tbody tr', name).within(() => cy.contains('button', 'Edit').click());
        cy.contains('.modal-header h3', heading).should('be.visible');
        setSelectField('Account', 'gce');
        cy.contains('.modal-footer button', 'Done').should('be.enabled').click();
        cy.contains('tbody tr', name).should('exist');
        cy.contains('button', 'Save Changes').click();
        cy.wait('@savePipeline');
        return;
      }

      cy.intercept('POST', '/tasks', (request) => {
        expect(request.body.job).to.have.length(1);
        const command = request.body.job[0];
        assertCommand(command, { expected, name, type });
        request.reply({ ref: '/tasks/gce-load-balancer-task' });
      }).as('createLoadBalancer');

      openInfrastructureModal(choice, heading);
      configure();
      cy.contains('.modal-footer button', 'Create').should('be.enabled').click();

      cy.wait('@createLoadBalancer');
    });
  });

  it('creates a NETWORK load balancer directly from infrastructure', () => {
    const { choice, expected, name, type } = loadBalancerTypes[0];
    cy.intercept('POST', '/tasks', (request) => {
      expect(request.body.job).to.have.length(1);
      assertCommand(request.body.job[0], { expected, name, type });
      request.reply({ ref: '/tasks/gce-load-balancer-task' });
    }).as('createNetworkLoadBalancer');

    openInfrastructureModal(choice, 'Create Network Load Balancer');
    setTextField('Name', name);
    setSelectField('Account', 'gce');
    setSelectField('Region', 'us-central1');
    cy.contains('.modal-footer button', 'Create').should('be.enabled').click();

    cy.wait('@createNetworkLoadBalancer');
  });

  it('round-trips a multi-operation HTTP load balancer through pipeline create and edit', () => {
    cy.visit(`#/applications/compute/executions/configure/${pipelineId}`);
    cy.contains('.pipeline-config-graph .label-body.node', 'Create Load Balancers').click({ force: true });
    cy.contains('button', 'Add load balancer').click();
    chooseType('HTTP(S)', 'Edit compute');

    cy.get('[data-testid="credentials"]').select('gce');
    cy.get('[data-testid="load-balancer-name"]').clear().should('have.value', '').type('pipeline-http');
    cy.get('[data-testid="load-balancer-name"]').should('have.value', 'pipeline-http');
    cy.get('[data-testid="listener-name"]').clear().type('pipeline-http-80');
    cy.get('[data-testid="default-backend-service"]').select('lb-backend');
    cy.contains('button', 'Add listener').click();
    cy.get('[data-testid="listener-name"]').last().clear().type('pipeline-http-8080');
    cy.get('[data-testid="listener-port"]').last().clear().type('8080');
    cy.contains('.modal-footer button', 'Done').should('be.enabled').click();

    assertPipelineOperation('pipeline-http-80');
    assertPipelineOperation('pipeline-http-8080');

    editPipelineOperation('pipeline-http-80', '80');
    assertPipelineOperation('pipeline-http-80');
    assertPipelineOperation('pipeline-http-8080');

    editPipelineOperation('pipeline-http-8080', '8080');
    assertPipelineOperation('pipeline-http-80');
    assertPipelineOperation('pipeline-http-8080');
  });
});

function openInfrastructureModal(choice, heading) {
  cy.visit('#/applications/compute/loadBalancers');
  cy.contains('button', 'Create Load Balancer').should('be.enabled').click();
  chooseType(choice, heading);
}

function chooseType(choice, heading) {
  cy.contains('.modal-title', 'Select Type of Load Balancer').should('exist');
  cy.contains('.load-balancer-label', new RegExp(`^${choice.replace(/[()]/g, '\\$&')}$`)).click({ force: true });
  cy.contains('.load-balancer-label', new RegExp(`^${choice.replace(/[()]/g, '\\$&')}$`))
    .parents('.card')
    .should('have.class', 'active');
  cy.contains('button', 'Configure Load Balancer').click();
  cy.contains('.modal-header h3', heading).should('be.visible');
}

function configureProxy(name) {
  setTextField('Name', name);
  setSelectField('Account', 'gce');
  setTextField('Health check name', `${name}-hc`);
  setNumberField('Health check port', '80');
}

function configureHttp(name, listenerName, selectBackend = true) {
  cy.get('[data-testid="credentials"]').select('gce');
  cy.get('[data-testid="load-balancer-name"]').clear().should('have.value', '').type(name);
  cy.get('[data-testid="load-balancer-name"]').should('have.value', name);
  cy.get('[data-testid="listener-name"]').first().clear().type(listenerName);
  if (selectBackend) {
    cy.get('[data-testid="default-backend-service"]').select('lb-backend');
  }
}

function setTextField(label, value) {
  cy.contains('.form-group label.control-label', new RegExp(`^${label}$`))
    .parents('.form-group')
    .find('input:not([type="number"])')
    .clear()
    .type(value);
}

function setNumberField(label, value) {
  cy.contains('.form-group label.control-label', new RegExp(`^${label}$`))
    .parents('.form-group')
    .find('input[type="number"]')
    .clear()
    .type(value);
}

function setSelectField(label, value) {
  cy.contains('.form-group label.control-label', new RegExp(`^${label}$`))
    .parents('.form-group')
    .find('select')
    .select(value);
}

function assertPipelineOperation(name) {
  cy.contains('tbody tr', name).within(() => {
    cy.contains('td', 'gce').should('exist');
    cy.contains('td', 'global').should('exist');
    cy.contains('button', 'Edit').should('exist');
  });
  cy.contains('tbody tr', name).should('have.length', 1).and('contain.text', name);
}

function editPipelineOperation(name, portRange) {
  cy.contains('tbody tr', name).within(() => cy.contains('button', 'Edit').click());
  cy.get('[data-testid="load-balancer-name"]').should('have.value', 'pipeline-http');
  cy.get('[data-testid="load-balancer-type"]').should('have.value', 'HTTP');
  cy.get('[data-testid="listener-name"]').should('have.value', name);
  cy.get('[data-testid="listener-port"]').should('have.value', portRange);
  cy.get('[data-testid="default-backend-service"]').should('have.value', 'lb-backend');
  cy.contains('.modal-footer button', 'Done').should('be.enabled').click();
}

function assertCommand(command, { expected, name, type }) {
  expect(command).to.deep.include({
    cloudProvider: 'gce',
    credentials: 'gce',
    loadBalancerName: name,
    loadBalancerType: type,
    name,
    provider: 'gce',
    type: 'upsertLoadBalancer',
    ...expected,
  });
  expect(command).not.to.have.property('listeners');
  expect(command).not.to.have.property('backendServices');
  expect(command).not.to.have.property('healthChecks');
  expect(command).not.to.have.property('mode');
}
