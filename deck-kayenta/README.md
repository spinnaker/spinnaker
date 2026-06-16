# Spinnaker Canary UI

## PR Process

In order to commit to this repo, please fork the repository and submit Pull Requests from a fork rather than a branch. It requires additional permissions to push branches.

## Development

Make sure that [node](http://nodejs.org/download/) and [pnpm](https://pnpm.io/installation)
are installed on your machine. The minimum versions for each are listed in `package.json`.

To develop this module, run it as a [Deck](https://github.com/spinnaker/deck) dependency using either `yalc` (recommended) or `npm link`.

### pnpm

In the root of this repository and the main Deck repository, run

```bash
pnpm install
```

For watch mode during development, run in the deck-kayenta directory:

```bash
pnpm watch
```

## Publishing @spinnaker/kayenta

This module is published as an NPM package.

- Create a pull request that increments `package.json`'s patch version - e.g., to `0.0.57`.
- Once the pull request has been merged, `@spinnaker/kayenta` will be automatically published to NPM via a GitHub Action.

Once `@spinnaker/kayenta` has been published, the Deck dependency on deck-kayenta will be automatically
updated by [Dependabot](https://github.com/spinnaker/deck/blob/master/.dependabot/config.yml). Keep an eye out
for a [PR](https://github.com/spinnaker/deck/pulls/app%2Fdependabot-preview) against Deck from Dependabot.

## Testing

To run `deck-kayenta`'s tests, run `pnpm test`.

To debug `deck-kayenta`' tests using https://jestjs.io/, run `pnpm test:debug`. Add a `debugger` statement to the test you want to debug.
