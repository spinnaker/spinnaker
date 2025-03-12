// https://github.com/cypress-io/cypress/issues/871#issuecomment-509392310
// Cypress will scroll an element into view before clicking.
// However, sometimes it scrolls an element underneath a sticky header.
// This changes scroll behavior so the element is scrolled to the middle of the container.
Cypress.on('scrolled', $el => {
  $el.get(0).scrollIntoView({
    block: 'center',
    inline: 'center',
  });
});
