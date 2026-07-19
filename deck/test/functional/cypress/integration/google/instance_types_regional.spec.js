import { registerDefaultFixtures } from '../../support';

describe('google: Regional Instance Type Distribution', () => {
  before(() => {
    require('events').EventEmitter.defaultMaxListeners = 15;
  });

  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/credentials?expand=true', {
      fixture: 'google/accelerator_zones/credentials.json',
    });

    cy.intercept('/images/find?*', {
      fixture: 'google/shared/images.json',
    });
  });

  const selectImage = () => {
    cy.contains('ul.steps-indicator li a', 'Image').click();
    cy.get('select[aria-label="Image"]:visible').select('ubuntu-1404-trusty-v20181002');
    cy.contains('ul.steps-indicator li a', 'Basic Settings').click();
  };

  it('should hide c4-standard-2 in europe-west1-d when regional is false', () => {
    cy.visit('#/applications/compute/clusters');
    cy.get('button:contains("Create Server Group")').click();

    selectImage();

    cy.get('select[aria-label="Region"]:visible').select('europe-west1');
    cy.contains('ul.steps-indicator li a', 'Capacity/Distribution').click();
    cy.get('input[aria-label="Regional server group"]').should('not.be.checked');
    cy.get('select[aria-label="Zone"]:visible').select('europe-west1-d');
    cy.contains('ul.steps-indicator li a', 'Instance Type').click();
    cy.contains('option', 'c4-standard-2').should('not.exist');
  });

  it('should show c4-standard-2 when regional is enabled without zone selection', () => {
    cy.visit('#/applications/compute/clusters');
    cy.get('button:contains("Create Server Group")').click();

    selectImage();

    cy.get('select[aria-label="Region"]:visible').select('europe-west1');
    cy.contains('ul.steps-indicator li a', 'Capacity/Distribution').click();
    cy.get('input[aria-label="Regional server group"]')
      .click({ force: true })
      .should('be.checked');

    cy.wait(1000);

    cy.contains('ul.steps-indicator li a', 'Instance Type').click();

    const maxAttempts = 3;
    let attempts = 0;

    const checkForInstanceType = () => {
      attempts++;
      cy.contains('c4-standard-2', { timeout: 5000 })
        .should('exist')
        .then(
          () => {},
          (err) => {
            if (attempts < maxAttempts) {
              cy.wait(1000);
              checkForInstanceType();
            } else {
              throw err;
            }
          },
        );
    };

    checkForInstanceType();
  });

  afterEach(() => {
    cy.window().then((win) => {
      win && win.removeAllListeners && win.removeAllListeners();
    });
  });
});
