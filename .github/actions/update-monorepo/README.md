# Update Monorepo Action

Github Action for pulling changes from individual repos into the monorepo

This uses TypeScript, and the output must be committed with each code change so Github can run it.  Github does not rebuild custom Actions.

Before committing a code change, run `npm run build`.

# Usage

GHA: Run the `Spinnaker Release` workflow from the Actions UI, and choose inputs

Local: Run `./local-action.sh` with prepended environment variables to supply inputs
