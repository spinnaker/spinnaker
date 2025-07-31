import {registerDefaultFixtures} from '../../support';

describe('kubernetes: Clusters', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications/applications/kubernetesapp/clusters', {
      fixture: 'kubernetes/clusters/clusters.json',
    });
    cy.intercept('/applications/kubernetesapp/serverGroupManagers', {
      fixture: 'kubernetes/clusters/serverGroupManagers.json',
    });
    cy.intercept('/applications/kubernetesapp/serverGroups', {
      fixture: 'kubernetes/clusters/serverGroups.json',
    });
    cy.intercept('/manifests/k8s-local/dev/deployment*', {
      fixture: 'kubernetes/manifests/deployment.json',
    });
    cy.intercept('/applications/kubernetesapp/serverGroups/k8s-local/dev/replicaSet*?includeDetails=false', {
      fixture: 'kubernetes/clusters/serverGroups/replicaset.json',
    });
    cy.intercept('/manifests/k8s-local/dev/replicaSet*', {
      fixture: 'kubernetes/manifests/replicaset.json',
    });
    cy.intercept('/manifests/k8s-local/dev/pod*', {
      fixture: 'kubernetes/manifests/pod.json',
    });
    cy.intercept('/instances/k8s-local/dev/pod*', {
      fixture: 'kubernetes/clusters/instances/pod.json',
    });
    cy.intercept('/instances/k8s-local/dev/pod*/console?provider=kubernetes', {
      fixture: 'kubernetes/clusters/instances/console.json',
    });
    cy.intercept('POST', '/tasks', {ref: '/tasks/01K17CHBN7Y358PSRE7GR04DC0'});
    cy.intercept('/tasks/01K17CHBN7Y358PSRE7GR04DC0', {
      fixture: 'kubernetes/task/task.success.json',
    });
  });

  it('should display the clusters section', () => {
    cy.visit('#/applications/kubernetesapp/clusters');

    cy.contains('.rollup-title-cell', 'deployment backend')
      .should('be.visible')
      .as('deploymentBackend');

    cy.get('@deploymentBackend')
      .parents('.rollup-entry')
      .within(() => {
        cy.get('.account-tag-name').should('contain.text', 'k8s-local');

        cy.get('.health-counts .instance-health-counts').eq(1).within(() => {
          cy.contains('2');
          cy.contains('100%');
        });

        cy.contains('.subgroup-title', 'dev').should('be.visible');

        cy.get('.server-group-title .icon-kubernetes').should('exist');
        cy.get('.server-group-title').should('contain.text', 'backend');

        cy.get('.server-group-sequence').should('contain.text', 'v001');
        cy.contains('nginx').should('exist');

        cy.get('.instances .instance-group-Up a.health-status-Up')
          .should('have.length', 2)
          .each(($el) => {
            cy.wrap($el).should('have.attr', 'title').and('match', /^pod backend-/);
          });
      });
  });

  it('should displays instance details table when "with details" is checked', () => {
    cy.visit('#/applications/kubernetesapp/clusters');

    cy.contains('label', 'Instances')
      .find('input[type="checkbox"]')
      .check({force: true});

    cy.contains('label', 'with details')
      .find('input[type="checkbox"]')
      .check({force: true});

    cy.get('.instance-list table thead tr').each($div => {
      cy.wrap($div).within(() => {
        cy.contains('th', 'Instance').should('exist');
        cy.contains('th', 'Launch Time').should('exist');
        cy.contains('th', 'Zone').should('exist');
        cy.contains('th', 'Provider').should('exist');
      });
    })

    cy.get('.instances tbody tr').should('have.length', 8);

    cy.get('.instances tbody tr').each($div => {
      cy.wrap($div).within(() => {
        cy.get('td').first().invoke('text').should('match', /^pod/);
        cy.contains('td', 'dev').should('exist');
        cy.contains('td', 'Up').should('exist');
      });
    })
  });

  it('should open pod details and validates content', () => {
    cy.visit('#/applications/kubernetesapp/clusters');

    cy.get('[title="pod backend-65b97dd546-vb8qf"]').click();

    cy.get('.InstanceDetailsHeader h3')
      .should('contain.text', 'backend-65b97dd546-vb8qf');

    cy.contains('h4', 'Information').should('be.visible');
    cy.contains('dt', 'Created').next('dd').should('contain.text', '2025-07-23');
    cy.contains('dt', 'Account').next('dd').should('contain.text', 'k8s-local');
    cy.contains('dt', 'Namespace').next('dd').should('contain.text', 'dev');
    cy.contains('dt', 'Kind').next('dd').should('contain.text', 'pod');
    cy.contains('dt', 'QOS Class').next('dd').should('contain.text', 'BestEffort');
    cy.contains('button', 'Console Output (Raw)').should('exist');

    cy.contains('button', 'Console Output (Raw)').click();
    cy.get('.console-output-tab').click();
    cy.get('.sp-modal-footer-right > .btn-primary').click();

    cy.contains('h4', 'Status').should('be.visible');
    [
      'PodReadyToStartContainers',
      'Initialized',
      'Ready',
      'ContainersReady',
      'PodScheduled',
    ].forEach(status => {
      cy.get('.collapsible-section')
        .contains('Status')
        .parent()
        .should('contain.text', status);
    });

    cy.contains('h4', 'Events').should('be.visible');
    [
      'Scheduled',
      'Pulled',
      'Created',
      'Started',
    ].forEach(event => {
      cy.get('.collapsible-section')
        .contains('Events')
        .parent()
        .should('contain.text', event);
    });

    cy.contains('h4', 'Resources').scrollIntoView().should('be.visible');
    cy.contains('Resource usage for backend').should('exist');
    cy.contains('dt', 'CPU').next('dd').should('contain.text', 'Unknown');
    cy.contains('dt', 'MEMORY').next('dd').should('contain.text', 'Unknown');

    cy.contains('h4', 'Labels').scrollIntoView().should('be.visible');
    [
      'app: backend',
      'app.kubernetes.io/managed-by: spinnaker',
      'app.kubernetes.io/name: kubernetes',
      'custom-label: custom-value',
      'pod-template-hash: 55f76f8479',
    ].forEach(label => {
      cy.get('.collapsible-section')
        .contains('Labels')
        .parent()
        .should('contain.text', label);
    });

    cy.contains('button', 'Pod Actions').click();
    cy.contains('a', 'Edit').click();

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Edit')
      .click();

    cy.get('.modal-content .modal-title').should('contain.text', 'Updating your manifest');
    cy.get('.overlay-modal-status ul.task-progress-refresh li strong').should('contain.text', 'Operation succeeded!');


    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Close')
      .click();

    cy.contains('button', 'Pod Actions').click();
    cy.contains('a', 'Delete').click();

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Submit')
      .click();

    cy.get('.modal-content .modal-title').should('contain.text', 'Deleting pod backend-65b97dd546-vb8qf in devDelete Pod backend-65b97dd546-vb8qf in dev');
    cy.get('.overlay-modal-status ul.task-progress-refresh li strong').should('contain.text', 'Operation succeeded!');

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Close')
      .click();
  });

  it('should open replicaset details and validate content', () => {
    cy.visit('#/applications/kubernetesapp/clusters');

    cy.get('.server-group.rollup-pod-server-group').eq(1).click();

    cy.get('.details-panel .header h3')
      .should('contain.text', 'backend-65b97dd546');

    cy.contains('h4', 'Information').should('be.visible');
    cy.contains('dt', 'Created').next('dd').should('contain.text', '2025-07-28');
    cy.contains('dt', 'Account').next('dd').should('contain.text', 'k8s-local');
    cy.contains('dt', 'Namespace').next('dd').should('contain.text', 'dev');
    cy.contains('dt', 'Kind').next('dd').should('contain.text', 'replicaSet');
    cy.contains('dt', 'Controller').next('a')
      .should('contain.text', 'Deployment backend');

    cy.contains('.collapsible-section', 'deployment info')
      .as('deploymentInfoSection')
      .should('exist')
      .within(() => {
        cy.contains('Account:').should('exist').parent().should('contain.text', 'k8s-local');
        cy.contains('Display Name:').should('exist').parent().should('contain.text', 'backend-65b97dd546');
      });

    cy.contains('h4', 'Images').should('be.visible');
    cy.contains('li', 'nginx:1.27.3').should('exist');

    cy.contains('h4', 'Events').should('be.visible');
    cy.contains('.collapsible-section', 'Events')
      .should('contain.text', 'No recent events found');

    const labels = [
      'app: backend',
      'app.kubernetes.io/managed-by: spinnaker',
      'app.kubernetes.io/name: kubernetes',
      'custom-label: custom-value',
      'pod-template-hash: 65b97dd546',
    ];
    cy.contains('h4', 'Labels').scrollIntoView().should('be.visible');
    labels.forEach(label => {
      cy.get('.collapsible-section')
        .contains('Labels')
        .parents()
        .should('contain.text', label);
    });

    cy.contains('h4', 'Size').scrollIntoView().should('be.visible');
    cy.contains('dt', 'Current').next('dd').should('contain.text', '2');

    cy.contains('h4', 'Health').scrollIntoView().should('be.visible');
    cy.contains('dt', 'Instances').next('dd').within(() => {
      cy.contains('2');
      cy.contains('100%');
    });

    cy.get('.details-panel .actions .dropdown-toggle')
      .should('contain.text', 'Replica Set Actions');

    cy.contains('button', 'Replica Set Actions').click();
    cy.contains('a', 'Delete').click();

    cy.get('.modal-content').within(() => {
      cy.get('.modal-title').should('contain.text', 'Delete ReplicaSet backend-65b97dd546 in dev');
      cy.get('.alert-warning').should('contain.text', 'Manifest is controlled by');
      cy.contains('a', 'Deployment backend').should('exist');

      cy.contains('Cascading').should('exist');
      cy.get('input[type="checkbox"]').should('exist');

      cy.contains('Grace Period').should('exist');
      cy.get('input[type="number"]').should('have.attr', 'min', '0');

      cy.contains('Reason').should('exist');
      cy.get('textarea[placeholder*="reason for this change"]').should('exist');

      cy.contains('button', 'Cancel').should('exist');
    });

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Submit')
      .click();

    cy.get('.modal-content .modal-title')
      .should('contain.text', 'Deleting replicaSet backend-65b97dd546 in devDelete ReplicaSet backend-65b97dd546 in dev');
    cy.get('.overlay-modal-status ul.task-progress-refresh li strong')
      .should('contain.text', 'Operation succeeded!');

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Close')
      .click();
  });

  it('should open deployment details and validate content', () => {
    cy.visit('#/applications/kubernetesapp/clusters');

    cy.get('.flex-container-h.server-group-title')
      .contains('deployment backend')
      .click();

    cy.get('.details-panel .header h3').should('contain.text', 'backend');
    cy.get('.details-panel .header .icon-kubernetes').should('exist');
    cy.contains('button', 'Deployment Actions').should('exist');

    cy.contains('h4', 'Information').should('be.visible');
    cy.contains('dt', 'Created').next('dd').should('contain.text', '2025-07-28');
    cy.contains('dt', 'Account').next('dd').should('contain.text', 'k8s-local');
    cy.contains('dt', 'Namespace').next('dd').should('contain.text', 'dev');
    cy.contains('dt', 'Kind').next('dd').should('contain.text', 'deployment');
    cy.contains('dt', 'Managing').next('dd')
      .find('a')
      .should('contain.text', 'replicaSet backend-65b97dd546');

    cy.contains('h4', 'Status').should('be.visible');
    cy.get('.collapsible-section')
      .contains('Status')
      .parent()
      .within(() => {
        cy.contains('Available').should('exist');
        cy.contains('Deployment has minimum availability.').should('exist');
        cy.contains('Progressing').should('exist');
        cy.contains('ReplicaSet "backend-65b97dd546" has successfully progressed.').should('exist');
      });

    cy.contains('.collapsible-section', 'deployment info')
      .as('deploymentInfoSection')
      .should('exist')
      .within(() => {
        cy.contains('Account:').should('exist').parent().should('contain.text', 'k8s-local');
        cy.contains('Display Name:').should('exist').parent().should('contain.text', 'backend');
      });

    cy.contains('h4', 'Events').scrollIntoView().should('be.visible');
    cy.get('.collapsible-section')
      .contains('Events')
      .parent()
      .should('contain.text', 'No recent events found');

    const labels = [
      'app: backend',
      'app.kubernetes.io/managed-by: spinnaker',
      'app.kubernetes.io/name: kubernetesapp',
    ];
    cy.contains('h4', 'Labels').scrollIntoView().should('be.visible');
    labels.forEach(label => {
      cy.get('.collapsible-section')
        .contains('Labels')
        .parent()
        .should('contain.text', label);
    });

    cy.contains('h4', 'Artifacts').scrollIntoView().should('be.visible');
    cy.get('.collapsible-section')
      .contains('Artifacts')
      .parent()
      .within(() => {
        cy.contains('docker/image').should('exist');
        cy.contains('nginx:1.27.3').should('exist');
      });

    cy.get('.details-panel .actions .dropdown-toggle')
      .should('contain.text', 'Deployment Actions');

    cy.contains('button', 'Deployment Actions').click();
    cy.contains('a', 'Scale').click();

    cy.get('.modal-content').within(() => {
      cy.get('.modal-title').should('contain.text', 'Scale Deployment backend in dev');

      cy.contains('Replicas').should('exist');
      cy.get('input[type="number"]').should('have.attr', 'min', '0');

      cy.contains('Reason').should('exist');
      cy.get('textarea[placeholder*="reason for this change"]').should('exist');

      cy.contains('button', 'Cancel').should('exist');
    });

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Submit')
      .click();

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Close')
      .click();

    cy.contains('button', 'Deployment Actions').click();
    cy.contains('a', 'Undo Rollout').click();

    cy.get('.modal-content').within(() => {
      cy.get('.modal-title').should('contain.text', 'Undo rollout of Deployment backend in dev');

      cy.contains('Revision').should('exist');
      cy.get('select').should('exist')

      cy.contains('Reason').should('exist');
      cy.get('textarea[placeholder*="reason for this change"]').should('exist');

      cy.contains('button', 'Cancel').should('exist');
    });

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Submit')
      .click();

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Close')
      .click();

    cy.contains('button', 'Deployment Actions').click();
    cy.contains('a', 'Rolling Restart').click();

    cy.get('.modal-content').within(() => {
      cy.get('.modal-title').should('contain.text', 'Initiate rolling restart of deployment backend');

      cy.contains('Reason').should('exist');
      cy.get('textarea[placeholder*="reason for this change"]').should('exist');

      cy.contains('button', 'Cancel').should('exist');
    });

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Confirm')
      .click();

    cy.get('.modal-footer button.btn.btn-primary')
      .contains('Close')
      .click();
  });

  it('should create a new cluster', () => {
    cy.visit('#/applications/kubernetesapp/clusters');

    cy.get('.application-actions')
      .contains('button', 'Create Server Group')
      .click();

    cy.wait(1000);

    cy.get('.modal-content').within(() => {
      cy.get('.modal-title').should('contain.text', 'Deploy Manifest');
      cy.contains('.form-group', 'Account')
        .find('select')
        .select('k8s-local');

      const manifest = `
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
        - name: nginx
          image: nginx:1.27.3
          ports:
            - containerPort: 80
`;
      cy.get('#yaml-editor textarea')
        .first()
        .invoke('val', manifest)
        .trigger('input', {force: true});

      cy.get('.modal-footer button.btn.btn-primary')
        .contains('Create')
        .click();

      cy.get('.modal-header .modal-title')
        .should('contain.text', 'Deploying your manifest');

      cy.get('.overlay-modal-status ul.task-progress-refresh li strong')
        .should('contain.text', 'Operation succeeded!');

      cy.get('.modal-footer button.btn.btn-primary')
        .contains('Close')
        .click();
    });
  });

});
