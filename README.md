# Spinnaker UI

![Branch Build](https://github.com/spinnaker/deck/workflows/Branch%20Build/badge.svg)

## Prerequisites

Make sure that [node](http://nodejs.org/download/) and [yarn](https://yarnpkg.com/en/docs/install) are installed on your system.
The minimum versions for each are listed in package.json.

## Quick Start

Run the following commands (in the deck directory) to get all dependencies installed in deck and to start the server:

- `yarn`
- `yarn modules`
- `yarn start`

The app will start up on localhost:9000.

When editing `core` or any other cloud provider package, please run the following in that folder

- `yarn dev`

If your local dev setup ends up in a corrupt state with missing npm modules, please run `yarn fixup` from deck and that
should reset your state.

## Environment variables

Environment variables can be used to configure application behavior. The following lists those variables and their possible values:

- `AUTH` enable/disable authentication (default is disabled, enable by setting `AUTH=enabled`).
- `TIMEZONE` set the default timezone (default is 'America/Los_Angeles' - see http://momentjs.com/timezone/docs/#/data-utilities/ for options)
- `DECK_CERT` enable SSL (set to the fully qualified path to cert file, and `DECK_KEY` must be set to the fully qualified path to the key file)

The following external resources can be specified with environment variables:

- `API_HOST` overrides the default Spinnaker API host.
- `AUTH_ENABLED` determines whether Deck will attempt to authenticate users via Gate.

For example, `API_HOST=http://spinnaker.prod.netflix.net yarn start` will run Deck with `http://spinnaker.prod.netflix.net` as the API host.

## Development

Deck has a combination of Angular and React, but is moving to React only. New changes made to the Deck project should use React wherever possible.

## Testing

To run the tests within the application, run `yarn test`.

Developing things locally? You may want to run [gate](https://github.com/spinnaker/gate) locally (which runs on port 8084) as well.
Gate is the service that hosts the spinnaker REST API.
Then run deck like this:

```
API_HOST=http://localhost:8084 yarn start
```

## Building &amp; Deploying

To build the application, run `yarn modules && yarn build`.
The built application lives in `build/`.

## Graphql

the `core` package is using graphql queries and mutation to interact with the backend (currently, only the `managed` components).
To generate the TS types and the Apollo hooks, run `yarn graphql:generate` from `core`.

## Conventions

It's a work in progress, but please try to follow the [conventions here](https://github.com/spinnaker/deck/wiki/Conventions).

## Customizing the UI

It's certainly doable - we're in the middle of some significant changes to our build process, which should make it easier.
For now, you can look at the [all modules](https://github.com/spinnaker/deck/tree/master/packages/) to
get an idea how we are customizing Deck internally. Expect a lot of this to change, though, as we figure out better, cleaner
hooks and integration points. And we're happy to provide new integration points (or accept pull requests) following
those existing conventions if you need an integration point that doesn't already exist.

## Join Us

Interested in sharing feedback on Spinnaker's UI or contributing to Deck?
Please join us at the [Spinnaker UI SIG](https://github.com/spinnaker/governance/tree/master/sig-ui-ux)!
