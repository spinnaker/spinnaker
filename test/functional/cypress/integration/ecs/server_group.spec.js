import { registerDefaultFixtures } from '../../support';

describe('amazon ecs: ECSApp Server Group', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.route('/applications/ecsapp/pipelines?**', 'fixture:ecs/pipelines/pipelines.json');
    cy.route('/images/find?*', 'fixture:ecs/shared/images.json');
    cy.route('/applications/ecsapp/pipelineConfigs', 'fixture:ecs/pipelines/pipelineConfigs.json');
    cy.route('/pipelineConfigs/**', 'fixture:ecs/pipelines/pipelineConfigs.json');
    cy.route('/networks/aws', 'fixture:ecs/default/networks.aws-ecs.json');
    cy.route('/applications/ecsapp/serverGroups', 'fixture:ecs/clusters/serverGroups.json');
    cy.route('/ecs/serviceDiscoveryRegistries', 'fixture:ecs/shared/serviceDiscoveryRegistries.json');
    cy.route('/ecs/ecsClusters', 'fixture:ecs/shared/ecsClusters.json');
    cy.route('/ecs/ecsClusterDescriptions/**', 'fixture:ecs/shared/ecsDescribeClusters.json');
    cy.route('/ecs/secrets', []);
    cy.route('/ecs/cloudMetrics/alarms', []);
    cy.route('/artifacts/credentials', 'fixture:ecs/shared/artifacts.json');
    cy.route('/roles/ecs', 'fixture:ecs/shared/roles.json');
    cy.route('/subnets/ecs', 'fixture:ecs/shared/subnets.json');
    cy.route('/loadBalancers?provider=ecs', 'fixture:ecs/shared/lb.json');
    cy.route('/applications/ecsapp/clusters', 'fixture:ecs/clusters/clusters.json');
    cy.route('/applications/ecsapp/loadBalancers', 'fixture:ecs/clusters/loadbalancers.json');
    cy.route(
      '/applications/ecsapp/serverGroups/**/aws-prod-ecsdemo-v000?includeDetails=false',
      'fixture:ecs/clusters/serverGroup.ecsdemo-v000.json',
    );
  });

  it('configure a new server group with artifacts', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click();
    cy.get('a:contains("Deploy")').click();

    cy.get('[data-test-id="Deploy.addServerGroup"]').click();
    cy.get('span:contains("Continue")').click();

    cy.get('[data-test-id="ServerGroup.clusterName"]').type('spinnaker-deployment-cluster');
    cy.get('span:contains("spinnaker-deployment-cluster")').click();

    cy.get('[data-test-id="ServerGroup.stack"]').type('create');
    cy.get('[data-test-id="ServerGroup.details"]').type('artifact');

    cy.get('[data-test-id="Networking.networkMode"]').type('awsvpc');
    cy.get('.Select-option:contains("awsvpc")').click();

    cy.get('[data-test-id="Networking.subnetType"]').type('public');
    cy.get('.Select-option:contains("public-subnet")').click();

    cy.get('[data-test-id="Networking.associatePublicIpAddressFalse"]').click();
    cy.get('[data-test-id="ServerGroup.useArtifacts"]').click();

    cy.get('task-definition-react .Select-placeholder:contains("Select an artifact")').type(' ');

    cy.get('task-definition-react .Select-option:contains("Define")').click();

    cy.get('task-definition-react input').eq(3).type('new-ecs-artifact');
    cy.get('task-definition-react input').eq(4).type('0.0.1');
    cy.get('task-definition-react input').eq(5).type('someLocation');
    cy.get('task-definition-react input').eq(6).type('someReference');

    cy.get('[data-test-id="Artifacts.containerAdd"]').click();
    cy.get('[data-test-id="Artifacts.containerName"]').type('v001-container');
    cy.get('[data-test-id="Artifacts.containerImage"]').type('TRIGGER');
    cy.get('.Select-option:contains("TRIGGER")').click();

    cy.get('[data-test-id="Artifacts.targetGroupAdd"]').click();
    cy.get('[data-test-id="Artifacts.targetGroupContainer"]').type('v001-container');
    cy.get('[data-test-id="Artifacts.targetGroup"]').type('demo');
    cy.get('.Select-option:contains("demo")').click();

    cy.get('[data-test-id="ServerGroup.launchType"]').type('FARGATE');
    cy.get('.ui-select-highlight:contains("FARGATE")').click();

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();

    cy.get('.account-tag').should('have.length', 2);
    cy.get('td:contains("ecsapp-prod-ecsdemo")').should('have.length', 1);
    cy.get('td:contains("ecsapp-create-artifact")').should('have.length', 1);
    cy.get('td:contains("us-west-2")').should('have.length', 2);

    cy.get('[data-test-id="Pipeline.revertChanges"]').click();
  });

  it('configure a new server group with container inputs', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click();
    cy.get('a:contains("Deploy")').click();

    cy.get('[data-test-id="Deploy.addServerGroup"]').click();
    cy.get('span:contains("Continue")').click();

    cy.get('[data-test-id="ServerGroup.clusterName"]').type('spinnaker-deployment-cluster');
    cy.get('span:contains("spinnaker-deployment-cluster")').click();

    cy.get('[data-test-id="ServerGroup.stack"]').type('create');
    cy.get('[data-test-id="ServerGroup.details"]').type('inputs');

    cy.get('[data-test-id="Networking.networkMode"]').type('awsvpc');
    cy.get('.Select-option:contains("awsvpc")').click();

    cy.get('[data-test-id="Networking.subnetType"]').type('public');
    cy.get('.Select-option:contains("public-subnet")').click();

    cy.get('[data-test-id="Networking.associatePublicIpAddressFalse"]').click();

    cy.get('[data-test-id="ContainerInputs.containerImage"]').type('TRIGGER');
    cy.get('.Select-option:contains("TRIGGER")').click();

    cy.get('[data-test-id="ContainerInputs.computeUnits"]').type(1024);
    cy.get('[data-test-id="ContainerInputs.reservedMemory"]').type(1024);

    cy.get('[data-test-id="ServerGroup.launchType"]').type('FARGATE');
    cy.get('.ui-select-highlight:contains("FARGATE")').click();

    cy.get('[data-test-id="Logging.logDriver"]').type('awslogs');
    cy.get('span:contains("awslogs")').click();

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();

    cy.get('.account-tag').should('have.length', 2);
    cy.get('td:contains("ecsapp-prod-ecsdemo")').should('have.length', 1);
    cy.get('td:contains("ecsapp-create-inputs")').should('have.length', 1);
    cy.get('td:contains("us-west-2")').should('have.length', 2);

    cy.get('[data-test-id="Pipeline.revertChanges"]').click();
  });

  it('edit an existing server group with container inputs', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click();
    cy.get('a:contains("Deploy")').click();
    cy.get('.glyphicon-edit').click();

    cy.get('[data-test-id="ServerGroup.stack"]').clear().type('edit');
    cy.get('[data-test-id="ServerGroup.details"]').clear().type('inputs');

    cy.get('[data-test-id="ServerGroup.useInputs"]').click();
    cy.get('[data-test-id="ContainerInputs.computeUnits"]').clear().type(1024);
    cy.get('[data-test-id="ContainerInputs.reservedMemory"]').clear().type(2048);

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();

    cy.get('.account-tag').should('have.length', 1);
    cy.get('td:contains("ecsapp-edit-inputs")').should('have.length', 1);
    cy.get('td:contains("us-west-2")').should('have.length', 1);

    cy.get('.glyphicon-edit').click();

    cy.get('[data-test-id="ContainerInputs.computeUnits"]').should('have.value', '1024');
    cy.get('[data-test-id="ContainerInputs.reservedMemory"]').should('have.value', '2048');

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();
    cy.get('[data-test-id="Pipeline.revertChanges"]').click();
  });

  it('edit an existing server group to use default capacity providers', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click();
    cy.get('a:contains("Deploy")').click();
    cy.get('.glyphicon-edit').click();

    cy.get('[data-test-id="ServerGroup.stack"]').clear().type('edit');
    cy.get('[data-test-id="ServerGroup.details"]').clear().type('computeOptions');
    cy.get('[data-test-id="ServerGroup.clusterName"]').type('example-app-test-Cluster-NSnYsTXmCfV2');
    cy.get('span:contains("example-app-test-Cluster-NSnYsTXmCfV2")').click();

    cy.get('[data-test-id="ServerGroup.computeOptionsCapacityProviders"]').click();
    cy.get('[data-test-id="ServerGroup.capacityProviders.default"]').click();

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();
    cy.get('.glyphicon-edit').click();

    cy.get('[data-test-id="ServerGroup.defaultCapacityProvider.name.0"]').should('have.value', 'FARGATE_SPOT');
    cy.get('[data-test-id="ServerGroup.capacityProvider.base.0"]').should('have.value', '0');
    cy.get('[data-test-id="ServerGroup.capacityProvider.weight.0"]').should('have.value', '1');

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();
    cy.get('[data-test-id="Pipeline.revertChanges"]').click();
  });

  it('edit an existing server group to use custom capacity providers', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click();
    cy.get('a:contains("Deploy")').click();
    cy.get('.glyphicon-edit').click();

    cy.get('[data-test-id="ServerGroup.stack"]').clear().type('edit');
    cy.get('[data-test-id="ServerGroup.details"]').clear().type('computeOptions');
    cy.get('[data-test-id="ServerGroup.clusterName"]').type('example-app-test-Cluster-NSnYsTXmCfV2');
    cy.get('span:contains("example-app-test-Cluster-NSnYsTXmCfV2")').click();

    cy.get('[data-test-id="ServerGroup.computeOptionsCapacityProviders"]').click();
    cy.get('[data-test-id="ServerGroup.capacityProviders.custom"]').click();
    cy.get('[data-test-id="ServerGroup.addCapacityProvider"]').click();

    cy.get('[data-test-id="ServerGroup.customCapacityProvider.name.0"]').type('FARGATE_SPOT');
    cy.get('.Select-option:contains("FARGATE_SPOT")').click();
    cy.get('[data-test-id="ServerGroup.capacityProvider.base.0"]').type(1);
    cy.get('[data-test-id="ServerGroup.capacityProvider.weight.0"]').type(2);

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();
    cy.get('.glyphicon-edit').click();

    cy.get('[data-test-id="ServerGroup.customCapacityProvider.name.0"]').find('.Select-value-label').should('have.text', 'FARGATE_SPOT');
    cy.get('[data-test-id="ServerGroup.capacityProvider.base.0"]').should('have.value', '1');
    cy.get('[data-test-id="ServerGroup.capacityProvider.weight.0"]').should('have.value', '2');

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();
    cy.get('[data-test-id="Pipeline.revertChanges"]').click();
  });

  it('edit an existing server group to enable SpEL processing for task def artifact', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click();
    cy.get('a:contains("Deploy")').click();
    cy.get('.glyphicon-edit').click();

    cy.get('[data-test-id="ServerGroup.stack"]').clear().type('edit');
    cy.get('[data-test-id="ServerGroup.details"]').clear().type('computeOptions');
    cy.get('[data-test-id="ServerGroup.clusterName"]').type('example-app-test-Cluster-NSnYsTXmCfV2');
    cy.get('span:contains("example-app-test-Cluster-NSnYsTXmCfV2")').click();


    cy.get('task-definition-react .evaluateTaskDef [type="checkbox"]').check()

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();
    cy.get('.glyphicon-edit').click();

    cy.get('.evaluateTaskDef [type="checkbox"]').check({force: true}).should('be.checked');

    cy.get('[data-test-id="ServerGroupWizard.submitButton"]').click();
    cy.get('[data-test-id="Pipeline.revertChanges"]').click();
  });
});
