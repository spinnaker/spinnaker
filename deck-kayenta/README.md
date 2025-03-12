# Spinnaker Canary UI

![Branch Build](https://github.com/spinnaker/deck-kayenta/workflows/Branch%20Build/badge.svg)

## PR Process

In order to commit to this repo, please fork the repository and submit Pull Requests from a fork rather than a branch. It requires additional permissions to push branches.

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

Next, run `yarn build --watch` at the root of this repository. In a separate terminal,
run `yarn start` at the root of the main Deck repository.

## Publishing @spinnaker/kayenta

This module is published as an NPM package.

- Create a pull request that increments `package.json`'s patch version - e.g., to `0.0.57`.
- Once the pull request has been merged, `@spinnaker/kayenta` will be automatically published to NPM via a GitHub Action.

Once `@spinnaker/kayenta` has been published, the Deck dependency on deck-kayenta will be automatically
updated by [Dependabot](https://github.com/spinnaker/deck/blob/master/.dependabot/config.yml). Keep an eye out
for a [PR](https://github.com/spinnaker/deck/pulls/app%2Fdependabot-preview) against Deck from Dependabot.

## Testing

To run `deck-kayenta`'s tests, run `yarn test`.

To debug `deck-kayenta`'s tests using https://jestjs.io/, run `yarn test:debug`. Add a `debugger` statement to the test you want to debug.
