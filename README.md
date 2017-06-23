# Spinnaker Canary UI

## Prerequisites

Make sure that [node](http://nodejs.org/download/) and [yarn](https://yarnpkg.com/en/docs/install) are installed on your
system. The minimum versions for each are listed in `package.json`.

## Quick Start

Run the following commands (in the root directory) to get all dependencies installed in deck and to start the server:

* `yarn`
* `yarn run start`

The app will start up on localhost:9000.

## Environment variables

Environment variables can be used to configure application behavior. The following lists those variables and their possible values:

* `AUTH` enable/disable authentication (default is disabled, enable by setting `AUTH=enabled`).
* `TIMEZONE` set the default timezone (default is 'America/Los_Angeles' - see http://momentjs.com/timezone/docs/#/data-utilities/ for options)
* `DECK_CERT` enable SSL (set to the fully qualified path to cert file, and `DECK_KEY` must be set to the fully qualified path to the key file)

The following external resources can be specified with environment variables:

* `API_HOST` overrides the default Spinnaker API host (https://api-prestaging.spinnaker.mgmt.netflix.net).
* `AUTH_ENABLED` determines whether Deck will attempt to authenticate users via Gate.

For example, `API_HOST=https://api.spinnaker.mgmt.netflix.net yarn run start` will run Deck with `https://api.spinnaker.mgmt.netflix.net` as the API host.


## Conventions

It's a work in progress, but please try to follow the [conventions here](https://github.com/spinnaker/deck/wiki/Conventions).


## Testing

To run the tests within the application, run `yarn run test`.

#### NOTE
Developing things locally? You will want to run [gate](https://github.com/spinnaker/gate) locally (which runs on port 8084) as well. It's the gateway to clouddriver. Then run deck like this:

```
API_HOST=http://localhost:8084 yarn run start
```



## Changing core/amazon/docker functionality?

We will soon split core/amazon/docker into separate repos in github, at which point these instructions will change.

If you're working on changes to the core library, i.e. [anything here](https://github.com/spinnaker/deck/tree/master/app/scripts/modules/core),
you have a couple of options for testing:

1. Run the OSS version of Deck, using the same environment variables listed above, or
2. Use `npm link` to see how the changes in core will affect the internal build.

### Using npm link
`npm link` is pretty straightforward:

1. From the command line, in the OSS repo, navigate to `/app/scripts/modules/core`
2. Type `npm link`
3. Navigate back to this repo, then type `npm link @spinnaker/core`
4. Run the netflix app as you normally would, i.e. `yarn run start`
5. From the OSS repo, in `/app/scripts/modules/core`, type `WATCH=true npm run lib`

(Steps 1-3 are a one-time setup - you won't need to do this every time.)

As you make changes in the core library, it will automatically be rebuilt, which will trigger a rebuild of the running
app. The turnaround is not super fast - it can take 20 to 30 seconds - but gives you a clear view of what the changes
will look like when deployed at Netflix.

### Publishing your changes to core/amazon/docker
This is very much a manual process right now.

1. Get the credentials from someone on Delivery Engineering
2. From the module you want to publish, e.g. `/app/scripts/modules/core`, rev the version number in `package.json` (for now, we are not following semver, so just rev the patch number)
3. Run `npm publish`, using the credentials from step 1.
4. Make sure to submit a PR to OSS to bump the version number!
5. Run `yarn upgrade @spinnaker/core@[new version number] --exact`
6. Submit a PR to commit the new library version.
