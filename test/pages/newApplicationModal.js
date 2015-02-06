module.exports = {
  header: element(by.css('[data-purpose="modal-header"]')),
  description: element(by.css('[data-purpose="application-description"]')),
  email: element(by.css('[data-purpose="application-email"]')),
  name: element(by.css('[data-purpose="application-name"]')),
  //prod: element(by.css('[data-purpose="application-accounts"] [value="prod"]')),
  prod: element(by.cssContainingText('option', 'prod')),
  submit: element(by.css('[data-purpose="submit"]')),
};
