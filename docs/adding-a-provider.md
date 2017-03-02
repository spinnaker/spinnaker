# Adding a Provider

This is a brief guide explaining what needs to be done to add support to
Halyard for an additional cloud provider.

Please read the README.md first to get a sense for the terminology &
functionality you'll be implementing.

> NOTE: this is missing rosco support, so configuring custom images isn't
> possible yet.

> NOTE: this project heavily uses [lombok](https://projectlombok.org/) to
> generate getters & setters using the class-level `@Data` annotation. This is
> very helpful since a lot of the classes you'll be defining are loaded &
> serialized as YAML, which requires getters & setters for each field to be
> serialized & deserialized respectively.

## 1. Create Account & Provider classes

The account class will be read directly from the __halconfig__ and written
(currently) without transformation to `clouddriver.yml`, so unless there is a
good reason, it's in the interest of simplicity to follow the format defined in
Clouddriver for your account class.

Here are examples:

  * [`Account`](https://github.com/spinnaker/halyard/blob/master/halyard-config/src/main/java/com/netflix/spinnaker/halyard/config/model/v1/providers/kubernetes/KubernetesAccount.java):
  The class needs to extend `Account`. Any fields that have options that can be
  determined at runtime, (e.g. `context`) can provide a class-method with
  signature `protected List<String>
  {%fieldName%}Options(ConfigProblemSetBuilder builder)` (e.g. `protected
  List<String> contextOptions(ConfigProblemSetBuilder builder)`) that returns
  a list of human-readable options for the field. Any fields that refer to
  files on host-machine's filesystem need to be annotated with `@LocalFile`.

  * [`Provider`](https://github.com/spinnaker/halyard/blob/master/halyard-config/src/main/java/com/netflix/spinnaker/halyard/config/model/v1/providers/kubernetes/KubernetesProvider.java):
  The class needs to extend `Provider<T extends Account>` where `T` is your 
  `Account` class. The code in this class is boilerplate.

## 2. Create an Account Validator

This step is likely the largest/most time-consuming.

The validator will be run on any of your provider's accounts when necessary
(updates, additions, dependent changes, etc...), and should catch common
user mistakes and report them as [`Problem`s](https://github.com/spinnaker/halyard/blob/master/halyard-core/src/main/java/com/netflix/spinnaker/halyard/core/problem/v1/Problem.java). 
Each problem requires:

  1. A [`severity`](https://github.com/spinnaker/halyard/blob/master/halyard-core/src/main/java/com/netflix/spinnaker/halyard/core/problem/v1/Problem.java#L25)
(read the comments at leach level for an understanding of when a certain level
applies).
  2. A human-readable `message` describing what the problem is.

Each problem can optionally provide:

  1. A human-readable `remediation` describing how to fix this problem.

There are also `options` & `location` fields that will be set by the validator.

To ensure that the validation matches what Clouddriver requires, please import
your provider's [Clouddriver
submodule](https://github.com/spinnaker/spinnaker-dependencies/blob/27985cab06f8ceafd327b175516e838e1f19f768/src/spinnaker-dependencies.template#L146).
An example of importing this submodule can be seen
[here](https://github.com/spinnaker/halyard/blob/master/halyard-config/halyard-config.gradle#L2).

Here is an example:

  * [`Validator`](https://github.com/spinnaker/halyard/blob/master/halyard-config/src/main/java/com/netflix/spinnaker/halyard/config/validate/v1/providers/kubernetes/KubernetesAccountValidator.java):
  This needs to extends `Validator<T extends Node>` where `T` is the type of
  the class you want to validate.

## 3. Create a CLI Command for your Provider

Here is an example:

  * [`Command`](https://github.com/spinnaker/halyard/blob/master/halyard-cli/src/main/java/com/netflix/spinnaker/halyard/cli/command/v1/config/providers/kubernetes/KubernetesCommand.java):
  This needs to extend `AbstractNamedProviderCommand`. We'll register the
  sample subcommands for your provider later.

Now once this command is
[registered](https://github.com/spinnaker/halyard/blob/master/halyard-cli/src/main/java/com/netflix/spinnaker/halyard/cli/command/v1/config/providers/ProviderCommand.java#L41)
you'll have all the generic subcommands at your disposal (`get-account`,
`delete-account`, `list-accounts`, `enable`, and `disable`) for your provider.

## 4. Create Add & Edit Commands

At this point it's valuable to point out that the command-line parameters are
being parsed with [JCommander](http://jcommander.org/). Any sort of flag
manipulation you want to do will need to go through this library.

The most work here goes into creating valuable descriptions for each flag.
There is no need to do any validation here (aside from setting `required=true`
for parameters that can't be omitted), since everything is handled __daemon__-side.

Here are examples:

  * [`AddAccount`](https://github.com/spinnaker/halyard/blob/master/halyard-cli/src/main/java/com/netflix/spinnaker/halyard/cli/command/v1/config/providers/google/GoogleAddAccountCommand.java): 
  Nothing fancy here.

  * [`EditAccount`](https://github.com/spinnaker/halyard/blob/master/halyard-cli/src/main/java/com/netflix/spinnaker/halyard/cli/command/v1/config/providers/google/GoogleEditAccountCommand.java):
  This needs to extend `AbstractEditAccountCommand<T extends Account>` where
  `T` is your provider's account type. The `isSet` and `updateStringList` 
  utility methods shown in the example will come in handy here.

Finally, register these subcommands with your provider's command as shown in
step 3.

## 5. Update settings.js
 
In order to have your settings be picked up in settings.js, you must make two changes.

First, [set up hal](https://github.com/spinnaker/halyard/blob/master/halyard-deploy/src/main/java/com/netflix/spinnaker/halyard/deploy/spinnaker/v1/profile/DeckProfile.java)
to inject parameters into settings.js. 
```javascript
bindings.put("kubernetes.default.account", kubernetesProvider.getPrimaryAccount());
bindings.put("kubernetes.default.namespace", "default");
bindings.put("kubernetes.default.proxy", "localhost:8001");

```


Then, update the [`settings.js`](https://github.com/spinnaker/deck/blob/master/halconfig/settings.js) 
that halyard grabs so that it can support injection of the variables you've defined for your provider.
The key defined above is replaced with the value given.
 
```javascript
var kubernetes = {
  defaults: {
    account: '{%kubernetes.default.account%}',
    namespace: '{%kubernetes.default.namespace%}',
    proxy: '{%kubernetes.default.proxy%}'
  }
};
```

Also make sure to set `providers.yourProvider` in the [same file](https://github.com/spinnaker/deck/blob/master/halconfig/settings.js)
to equal the variable for your provider.
```javascript
providers: {
    ...
    kubernetes: kubernetes, 
  },
```
