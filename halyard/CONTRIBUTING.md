# Contributing

Pull requests are welcome!

## Getting Started

First [fork this repo](https://help.github.com/articles/fork-a-repo/), and
then clone it & set the upstream repo:

```bash
git clone git@github.com:your-github-username/halyard.git

   # In my case (username is lwander), the above command looks like:
   # git clone git@github.com:lwander/halyard.git

cd halyard

git remote add upstream git@github.com:spinnaker/halyard.git

  # The above command allows you to push patches to the upstream repo for 
  # approval.
```

## Configuring Your Machine

If you are developing on a mac, you will need [Xcode](https://developer.apple.com/xcode/) installed and you will need to open it once to agree to the license. 
If you don't have it, you might see an error when you try to build the CLI (`xcrun: error: invalid active developer path`).

## Configuring IntelliJ

In the project's root directory run:

```bash
./gradlew idea
```

and then point IntelliJ at the generated project. You shouldn't need to import
this project, simply navigating to __File__ > __Open__ > _the project's root
directory_ should be enough.

## Running the Daemon

In the project's root directory run:

```bash
./gradlew
```

and the daemon will build & run. The first time you do this it will take a few
minutes as it fetches the dependencies.

## Running the Command-Line Interface (CLI)

In the project's root directory run:

```bash
cd halyard-cli/

make

./hal --help
```

> WARNING! Since this project uses one gradle project for two executables, you
> currently can't run `make` and `./gradlew` at the same time (one of the two
> will likely hang indefinitely).

## Adding Functionality

* [Adding a config parameter](/docs/adding-a-config-parameter.md)
* [Adding a provider](/docs/adding-a-provider.md)

## Deprecating Functionality

* [Deprecating a command](/docs/deprecating-commands.md)
* [Deprecating a field](/docs/deprecating-fields.md)
