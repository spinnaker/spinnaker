# Spinnaker Canary UI

[![Build Status](https://travis-ci.org/spinnaker/deck-kayenta.png?branch=master)](https://travis-ci.org/spinnaker/deck-kayenta)

## Development

Make sure that [node](http://nodejs.org/download/) and [yarn](https://yarnpkg.com/en/docs/install)
are installed on your machine. The minimum versions for each are listed in `package.json`.

To develop this module, run it as a [Deck](https://github.com/spinnaker/deck) dependency using either `yalc` (recommended) or `npm link`.

### yarn

In the root of this repository and the main Deck repository, run
```bash
yarn
```

### yalc

Globally install [yalc](https://github.com/whitecolor/yalc).

From the root of this repository, run

```bash
yalc publish
```

From the root of the main Deck repository, run

```bash
yalc add @spinnaker/kayenta
yarn start
```

As you make additional changes in this repository, run

```bash
yalc publish --push
```

### npm link

From the root of this repository, run

```bash
npm link
```

From the root of the main Deck repository, run

```bash
npm link @spinnaker/kayenta
```

You should only have to run these commands once.

Next, run `WATCH=true yarn lib` at the root of this repository. In a separate terminal,
run `yarn start` at the root of the main Deck repository.

### Environment Variables

`deck-kayenta` uses feature and development flags. These are fully configurable within `settings.js`,
but it is usually easier to pass the flags as environment variables.

These are good defaults:

```bash
REDUX_LOGGER=true \
API_HOST=http://localhost:8084 \
METRIC_STORE=stackdriver \
CANARY_STAGES_ENABLED=true \
TEMPLATES_ENABLED=true \
yarn start
```

## Publishing @spinnaker/kayenta

This module is published as an NPM package.

- Create a pull request that increments `package.json`'s patch version - e.g., to `0.0.57`.
- Once the pull request has been merged, publish a release using the same tag as `package.json`'s version, e.g., `v0.0.57`. `@spinnaker/kayenta` will be automatically published to NPM.

Once `@spinnaker/kayenta` has been published, the Deck dependency on deck-kayenta will be automatically
updated by [Dependabot](https://github.com/spinnaker/deck/blob/master/.dependabot/config.yml). Keep an eye out
for a [PR](https://github.com/spinnaker/deck/pulls/app%2Fdependabot-preview) against Deck from Dependabot.

## Testing

To run `deck-kayenta`'s tests, run `yarn test`.

To run `deck-kayenta`'s tests and generate a code coverage report, run `yarn test-coverage`.
Open `/coverage/html/index.html` in a browser to view the HTML version of the report.
