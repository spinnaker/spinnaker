

# Table of Contents


 * [**hal**](#hal)
 * [**hal admin**](#hal-admin)
 * [**hal admin deprecate**](#hal-admin-deprecate)
 * [**hal admin deprecate version**](#hal-admin-deprecate-version)
 * [**hal admin publish**](#hal-admin-publish)
 * [**hal admin publish bom**](#hal-admin-publish-bom)
 * [**hal admin publish latest**](#hal-admin-publish-latest)
 * [**hal admin publish latest-halyard**](#hal-admin-publish-latest-halyard)
 * [**hal admin publish latest-spinnaker**](#hal-admin-publish-latest-spinnaker)
 * [**hal admin publish profile**](#hal-admin-publish-profile)
 * [**hal admin publish version**](#hal-admin-publish-version)
 * [**hal backup**](#hal-backup)
 * [**hal backup create**](#hal-backup-create)
 * [**hal config**](#hal-config)
 * [**hal config ci**](#hal-config-ci)
 * [**hal config ci jenkins**](#hal-config-ci-jenkins)
 * [**hal config ci jenkins disable**](#hal-config-ci-jenkins-disable)
 * [**hal config ci jenkins enable**](#hal-config-ci-jenkins-enable)
 * [**hal config ci jenkins master**](#hal-config-ci-jenkins-master)
 * [**hal config ci jenkins master add**](#hal-config-ci-jenkins-master-add)
 * [**hal config ci jenkins master delete**](#hal-config-ci-jenkins-master-delete)
 * [**hal config ci jenkins master edit**](#hal-config-ci-jenkins-master-edit)
 * [**hal config ci jenkins master get**](#hal-config-ci-jenkins-master-get)
 * [**hal config ci jenkins master list**](#hal-config-ci-jenkins-master-list)
 * [**hal config deploy**](#hal-config-deploy)
 * [**hal config deploy edit**](#hal-config-deploy-edit)
 * [**hal config features**](#hal-config-features)
 * [**hal config features edit**](#hal-config-features-edit)
 * [**hal config generate**](#hal-config-generate)
 * [**hal config metric-stores**](#hal-config-metric-stores)
 * [**hal config metric-stores datadog**](#hal-config-metric-stores-datadog)
 * [**hal config metric-stores datadog disable**](#hal-config-metric-stores-datadog-disable)
 * [**hal config metric-stores datadog edit**](#hal-config-metric-stores-datadog-edit)
 * [**hal config metric-stores datadog enable**](#hal-config-metric-stores-datadog-enable)
 * [**hal config metric-stores edit**](#hal-config-metric-stores-edit)
 * [**hal config metric-stores prometheus**](#hal-config-metric-stores-prometheus)
 * [**hal config metric-stores prometheus disable**](#hal-config-metric-stores-prometheus-disable)
 * [**hal config metric-stores prometheus edit**](#hal-config-metric-stores-prometheus-edit)
 * [**hal config metric-stores prometheus enable**](#hal-config-metric-stores-prometheus-enable)
 * [**hal config metric-stores stackdriver**](#hal-config-metric-stores-stackdriver)
 * [**hal config metric-stores stackdriver disable**](#hal-config-metric-stores-stackdriver-disable)
 * [**hal config metric-stores stackdriver edit**](#hal-config-metric-stores-stackdriver-edit)
 * [**hal config metric-stores stackdriver enable**](#hal-config-metric-stores-stackdriver-enable)
 * [**hal config provider**](#hal-config-provider)
 * [**hal config provider appengine**](#hal-config-provider-appengine)
 * [**hal config provider appengine account**](#hal-config-provider-appengine-account)
 * [**hal config provider appengine account add**](#hal-config-provider-appengine-account-add)
 * [**hal config provider appengine account delete**](#hal-config-provider-appengine-account-delete)
 * [**hal config provider appengine account edit**](#hal-config-provider-appengine-account-edit)
 * [**hal config provider appengine account get**](#hal-config-provider-appengine-account-get)
 * [**hal config provider appengine account list**](#hal-config-provider-appengine-account-list)
 * [**hal config provider appengine disable**](#hal-config-provider-appengine-disable)
 * [**hal config provider appengine enable**](#hal-config-provider-appengine-enable)
 * [**hal config provider aws**](#hal-config-provider-aws)
 * [**hal config provider aws account**](#hal-config-provider-aws-account)
 * [**hal config provider aws account add**](#hal-config-provider-aws-account-add)
 * [**hal config provider aws account delete**](#hal-config-provider-aws-account-delete)
 * [**hal config provider aws account edit**](#hal-config-provider-aws-account-edit)
 * [**hal config provider aws account get**](#hal-config-provider-aws-account-get)
 * [**hal config provider aws account list**](#hal-config-provider-aws-account-list)
 * [**hal config provider aws disable**](#hal-config-provider-aws-disable)
 * [**hal config provider aws edit**](#hal-config-provider-aws-edit)
 * [**hal config provider aws enable**](#hal-config-provider-aws-enable)
 * [**hal config provider azure**](#hal-config-provider-azure)
 * [**hal config provider azure account**](#hal-config-provider-azure-account)
 * [**hal config provider azure account add**](#hal-config-provider-azure-account-add)
 * [**hal config provider azure account delete**](#hal-config-provider-azure-account-delete)
 * [**hal config provider azure account edit**](#hal-config-provider-azure-account-edit)
 * [**hal config provider azure account get**](#hal-config-provider-azure-account-get)
 * [**hal config provider azure account list**](#hal-config-provider-azure-account-list)
 * [**hal config provider azure bakery**](#hal-config-provider-azure-bakery)
 * [**hal config provider azure bakery base-image**](#hal-config-provider-azure-bakery-base-image)
 * [**hal config provider azure bakery base-image add**](#hal-config-provider-azure-bakery-base-image-add)
 * [**hal config provider azure bakery base-image delete**](#hal-config-provider-azure-bakery-base-image-delete)
 * [**hal config provider azure bakery base-image edit**](#hal-config-provider-azure-bakery-base-image-edit)
 * [**hal config provider azure bakery base-image get**](#hal-config-provider-azure-bakery-base-image-get)
 * [**hal config provider azure bakery base-image list**](#hal-config-provider-azure-bakery-base-image-list)
 * [**hal config provider azure bakery edit**](#hal-config-provider-azure-bakery-edit)
 * [**hal config provider azure disable**](#hal-config-provider-azure-disable)
 * [**hal config provider azure enable**](#hal-config-provider-azure-enable)
 * [**hal config provider dcos**](#hal-config-provider-dcos)
 * [**hal config provider dcos account**](#hal-config-provider-dcos-account)
 * [**hal config provider dcos account add**](#hal-config-provider-dcos-account-add)
 * [**hal config provider dcos account delete**](#hal-config-provider-dcos-account-delete)
 * [**hal config provider dcos account edit**](#hal-config-provider-dcos-account-edit)
 * [**hal config provider dcos account get**](#hal-config-provider-dcos-account-get)
 * [**hal config provider dcos account list**](#hal-config-provider-dcos-account-list)
 * [**hal config provider dcos cluster**](#hal-config-provider-dcos-cluster)
 * [**hal config provider dcos cluster add**](#hal-config-provider-dcos-cluster-add)
 * [**hal config provider dcos cluster delete**](#hal-config-provider-dcos-cluster-delete)
 * [**hal config provider dcos cluster edit**](#hal-config-provider-dcos-cluster-edit)
 * [**hal config provider dcos cluster get**](#hal-config-provider-dcos-cluster-get)
 * [**hal config provider dcos cluster list**](#hal-config-provider-dcos-cluster-list)
 * [**hal config provider dcos disable**](#hal-config-provider-dcos-disable)
 * [**hal config provider dcos enable**](#hal-config-provider-dcos-enable)
 * [**hal config provider docker-registry**](#hal-config-provider-docker-registry)
 * [**hal config provider docker-registry account**](#hal-config-provider-docker-registry-account)
 * [**hal config provider docker-registry account add**](#hal-config-provider-docker-registry-account-add)
 * [**hal config provider docker-registry account delete**](#hal-config-provider-docker-registry-account-delete)
 * [**hal config provider docker-registry account edit**](#hal-config-provider-docker-registry-account-edit)
 * [**hal config provider docker-registry account get**](#hal-config-provider-docker-registry-account-get)
 * [**hal config provider docker-registry account list**](#hal-config-provider-docker-registry-account-list)
 * [**hal config provider docker-registry disable**](#hal-config-provider-docker-registry-disable)
 * [**hal config provider docker-registry enable**](#hal-config-provider-docker-registry-enable)
 * [**hal config provider google**](#hal-config-provider-google)
 * [**hal config provider google account**](#hal-config-provider-google-account)
 * [**hal config provider google account add**](#hal-config-provider-google-account-add)
 * [**hal config provider google account delete**](#hal-config-provider-google-account-delete)
 * [**hal config provider google account edit**](#hal-config-provider-google-account-edit)
 * [**hal config provider google account get**](#hal-config-provider-google-account-get)
 * [**hal config provider google account list**](#hal-config-provider-google-account-list)
 * [**hal config provider google bakery**](#hal-config-provider-google-bakery)
 * [**hal config provider google bakery base-image**](#hal-config-provider-google-bakery-base-image)
 * [**hal config provider google bakery base-image add**](#hal-config-provider-google-bakery-base-image-add)
 * [**hal config provider google bakery base-image delete**](#hal-config-provider-google-bakery-base-image-delete)
 * [**hal config provider google bakery base-image edit**](#hal-config-provider-google-bakery-base-image-edit)
 * [**hal config provider google bakery base-image get**](#hal-config-provider-google-bakery-base-image-get)
 * [**hal config provider google bakery base-image list**](#hal-config-provider-google-bakery-base-image-list)
 * [**hal config provider google bakery edit**](#hal-config-provider-google-bakery-edit)
 * [**hal config provider google disable**](#hal-config-provider-google-disable)
 * [**hal config provider google enable**](#hal-config-provider-google-enable)
 * [**hal config provider kubernetes**](#hal-config-provider-kubernetes)
 * [**hal config provider kubernetes account**](#hal-config-provider-kubernetes-account)
 * [**hal config provider kubernetes account add**](#hal-config-provider-kubernetes-account-add)
 * [**hal config provider kubernetes account delete**](#hal-config-provider-kubernetes-account-delete)
 * [**hal config provider kubernetes account edit**](#hal-config-provider-kubernetes-account-edit)
 * [**hal config provider kubernetes account get**](#hal-config-provider-kubernetes-account-get)
 * [**hal config provider kubernetes account list**](#hal-config-provider-kubernetes-account-list)
 * [**hal config provider kubernetes disable**](#hal-config-provider-kubernetes-disable)
 * [**hal config provider kubernetes edit**](#hal-config-provider-kubernetes-edit)
 * [**hal config provider kubernetes enable**](#hal-config-provider-kubernetes-enable)
 * [**hal config provider openstack**](#hal-config-provider-openstack)
 * [**hal config provider openstack account**](#hal-config-provider-openstack-account)
 * [**hal config provider openstack account add**](#hal-config-provider-openstack-account-add)
 * [**hal config provider openstack account delete**](#hal-config-provider-openstack-account-delete)
 * [**hal config provider openstack account edit**](#hal-config-provider-openstack-account-edit)
 * [**hal config provider openstack account get**](#hal-config-provider-openstack-account-get)
 * [**hal config provider openstack account list**](#hal-config-provider-openstack-account-list)
 * [**hal config provider openstack disable**](#hal-config-provider-openstack-disable)
 * [**hal config provider openstack enable**](#hal-config-provider-openstack-enable)
 * [**hal config provider oraclebmcs**](#hal-config-provider-oraclebmcs)
 * [**hal config provider oraclebmcs account**](#hal-config-provider-oraclebmcs-account)
 * [**hal config provider oraclebmcs account add**](#hal-config-provider-oraclebmcs-account-add)
 * [**hal config provider oraclebmcs account delete**](#hal-config-provider-oraclebmcs-account-delete)
 * [**hal config provider oraclebmcs account edit**](#hal-config-provider-oraclebmcs-account-edit)
 * [**hal config provider oraclebmcs account get**](#hal-config-provider-oraclebmcs-account-get)
 * [**hal config provider oraclebmcs account list**](#hal-config-provider-oraclebmcs-account-list)
 * [**hal config provider oraclebmcs disable**](#hal-config-provider-oraclebmcs-disable)
 * [**hal config provider oraclebmcs enable**](#hal-config-provider-oraclebmcs-enable)
 * [**hal config security**](#hal-config-security)
 * [**hal config security api**](#hal-config-security-api)
 * [**hal config security api edit**](#hal-config-security-api-edit)
 * [**hal config security api ssl**](#hal-config-security-api-ssl)
 * [**hal config security api ssl disable**](#hal-config-security-api-ssl-disable)
 * [**hal config security api ssl edit**](#hal-config-security-api-ssl-edit)
 * [**hal config security api ssl enable**](#hal-config-security-api-ssl-enable)
 * [**hal config security authn**](#hal-config-security-authn)
 * [**hal config security authn oauth2**](#hal-config-security-authn-oauth2)
 * [**hal config security authn oauth2 disable**](#hal-config-security-authn-oauth2-disable)
 * [**hal config security authn oauth2 edit**](#hal-config-security-authn-oauth2-edit)
 * [**hal config security authn oauth2 enable**](#hal-config-security-authn-oauth2-enable)
 * [**hal config security authn saml**](#hal-config-security-authn-saml)
 * [**hal config security authn saml disable**](#hal-config-security-authn-saml-disable)
 * [**hal config security authn saml edit**](#hal-config-security-authn-saml-edit)
 * [**hal config security authn saml enable**](#hal-config-security-authn-saml-enable)
 * [**hal config security authz**](#hal-config-security-authz)
 * [**hal config security authz disable**](#hal-config-security-authz-disable)
 * [**hal config security authz edit**](#hal-config-security-authz-edit)
 * [**hal config security authz enable**](#hal-config-security-authz-enable)
 * [**hal config security authz github**](#hal-config-security-authz-github)
 * [**hal config security authz github edit**](#hal-config-security-authz-github-edit)
 * [**hal config security authz google**](#hal-config-security-authz-google)
 * [**hal config security authz google edit**](#hal-config-security-authz-google-edit)
 * [**hal config security ui**](#hal-config-security-ui)
 * [**hal config security ui edit**](#hal-config-security-ui-edit)
 * [**hal config security ui ssl**](#hal-config-security-ui-ssl)
 * [**hal config security ui ssl disable**](#hal-config-security-ui-ssl-disable)
 * [**hal config security ui ssl edit**](#hal-config-security-ui-ssl-edit)
 * [**hal config security ui ssl enable**](#hal-config-security-ui-ssl-enable)
 * [**hal config storage**](#hal-config-storage)
 * [**hal config storage azs**](#hal-config-storage-azs)
 * [**hal config storage azs edit**](#hal-config-storage-azs-edit)
 * [**hal config storage edit**](#hal-config-storage-edit)
 * [**hal config storage gcs**](#hal-config-storage-gcs)
 * [**hal config storage gcs edit**](#hal-config-storage-gcs-edit)
 * [**hal config storage oraclebmcs**](#hal-config-storage-oraclebmcs)
 * [**hal config storage oraclebmcs edit**](#hal-config-storage-oraclebmcs-edit)
 * [**hal config storage s3**](#hal-config-storage-s3)
 * [**hal config storage s3 edit**](#hal-config-storage-s3-edit)
 * [**hal config version**](#hal-config-version)
 * [**hal config version edit**](#hal-config-version-edit)
 * [**hal deploy**](#hal-deploy)
 * [**hal deploy apply**](#hal-deploy-apply)
 * [**hal deploy clean**](#hal-deploy-clean)
 * [**hal deploy collect-logs**](#hal-deploy-collect-logs)
 * [**hal deploy connect**](#hal-deploy-connect)
 * [**hal deploy details**](#hal-deploy-details)
 * [**hal deploy diff**](#hal-deploy-diff)
 * [**hal deploy rollback**](#hal-deploy-rollback)
 * [**hal task**](#hal-task)
 * [**hal task interrupt**](#hal-task-interrupt)
 * [**hal task list**](#hal-task-list)
 * [**hal version**](#hal-version)
 * [**hal version bom**](#hal-version-bom)
 * [**hal version latest**](#hal-version-latest)
 * [**hal version list**](#hal-version-list)
## hal

A tool for configuring, installing, and updating Spinnaker.

If this is your first time using Halyard to install Spinnaker we recommend that you skim the documentation on www.spinnaker.io/docs for some familiarity with the product. If at any point you get stuck using 'hal', every command can be suffixed with '--help' for usage information.


#### Usage
```
hal [parameters] [subcommands]
```
#### Global Parameters
 * `--options`: Get options for the specified field name.
 * `-c, --color`: Enable terminal color output.
 * `-d, --debug`: Show detailed network traffic with halyard daemon.
 * `-h, --help`: (*Default*: `false`) Display help text about this command.
 * `-l, --log`: Set the log level of the CLI.
 * `-o, --output`: Format the CLIs output.
 * `-q, --quiet`: Show no task information or messages. When disabled, ANSI formatting will be disabled too.

#### Parameters
 * `--docs`: (*Default*: `false`) Print markdown docs for the hal CLI.
 * `--print-bash-completion`: (*Default*: `false`) Print bash command completion. This is used during the installation of Halyard.
 * `--ready`: (*Default*: `false`) Check if Halyard is up and running. Will exit with non-zero return code when it isn't.
 * `--version, -v`: (*Default*: `false`) Version of Halyard.

#### Subcommands
 * `admin`: This is meant for users building and publishing their own Spinnaker images and config.
 * `backup`: Backup and restore (remote or local) copies of your halconfig and all required files.
 * `config`: Configure, validate, and view your halconfig.
 * `deploy`: Manage the deployment of Spinnaker. This includes where it's deployed, what the infrastructure footprint looks like, what the currently running deployment looks like, etc...
 * `task`: This set of commands exposes utilities of dealing with Halyard's task engine.
 * `version`: Get information about the available Spinnaker versions.

---
## hal admin

This is meant for users building and publishing their own Spinnaker images and config.

#### Usage
```
hal admin [subcommands]
```

#### Subcommands
 * `deprecate`: Deprecate config artifacts in your configured halconfig bucket.
 * `publish`: Publish config artifacts to your configured halconfig bucket.

---
## hal admin deprecate

Deprecate config artifacts in your configured halconfig bucket.

#### Usage
```
hal admin deprecate [subcommands]
```

#### Subcommands
 * `version`: Deprecate a version of Spinnaker, removing it from the global versions.yml tracking file.

---
## hal admin deprecate version

Deprecate a version of Spinnaker, removing it from the global versions.yml tracking file.

#### Usage
```
hal admin deprecate version [parameters]
```

#### Parameters
 * `--illegal-reason`: If supplied, the version will not only be deprecated, but will no longer be installable by Halyard for the supplied reason
 * `--version`: (*Required*) The version (x.y.z) of Spinnaker to be deprecated.


---
## hal admin publish

Publish config artifacts to your configured halconfig bucket.

#### Usage
```
hal admin publish [subcommands]
```

#### Subcommands
 * `bom`: Publish a Bill of Materials (BOM).
 * `latest` _(Deprecated)_ : Publish the latest version of Spinnaker to the global versions.yml tracking file.
 * `latest-halyard`: Publish the latest version of Halyard to the global versions.yml tracking file.
 * `latest-spinnaker`: Publish the latest version of Spinnaker to the global versions.yml tracking file.
 * `profile`: Publish a base halconfig profile for a specific Spinnaker artifact.
 * `version`: Publish a version of Spinnaker to the global versions.yml tracking file.

---
## hal admin publish bom

Publish a Bill of Materials (BOM).

#### Usage
```
hal admin publish bom [parameters]
```

#### Parameters
 * `--bom-path`: (*Required*) The path to the BOM owning the artifact to publish.


---
## hal admin publish latest

Publish the latest version of Spinnaker to the global versions.yml tracking file.

#### Usage
```
hal admin publish latest VERSION
```


---
## hal admin publish latest-halyard

Publish the latest version of Halyard to the global versions.yml tracking file.

#### Usage
```
hal admin publish latest-halyard VERSION
```


---
## hal admin publish latest-spinnaker

Publish the latest version of Spinnaker to the global versions.yml tracking file.

#### Usage
```
hal admin publish latest-spinnaker VERSION
```


---
## hal admin publish profile

Publish a base halconfig profile for a specific Spinnaker artifact.

#### Usage
```
hal admin publish profile ARTIFACT-NAME [parameters]
```

#### Parameters
`ARTIFACT-NAME`: The name of the artifact whose profile is being published (e.g. clouddriver).
 * `--bom-path`: (*Required*) The path to the BOM owning the artifact to publish.
 * `--profile-path`: (*Required*) The path to the artifact profile to publish.


---
## hal admin publish version

Publish a version of Spinnaker to the global versions.yml tracking file.

#### Usage
```
hal admin publish version [parameters]
```

#### Parameters
 * `--alias`: (*Required*) The alias this version of Spinnaker goes by.
 * `--changelog`: (*Required*) A link to this Spinnaker release's changelog.
 * `--version`: (*Required*) The version (x.y.z) of Spinnaker to be recorded. This must exist as a BOM.


---
## hal backup

This is used to periodically checkpoint your configured Spinnaker installation as well as allow you to remotely store all aspects of your configured Spinnaker installation.

#### Usage
```
hal backup [subcommands]
```

#### Subcommands
 * `create`: Create a backup.

---
## hal backup create

Create a backup.

#### Usage
```
hal backup create
```


---
## hal config

Configure, validate, and view your halconfig.

#### Usage
```
hal config [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--set-current-deployment`: If supplied, set the current active deployment to the supplied value, creating it if need-be.

#### Subcommands
 * `ci`: Configure, validate, and view the specified Continuous Integration service.
 * `deploy`: Display the configured Spinnaker deployment.
 * `features`: Display the state of Spinnaker's feature flags.
 * `generate`: Generate the full Spinnaker config for your current deployment.
 * `metric-stores`: Configure Spinnaker's metric stores. This configuration only affects the publishing of metrics against whichever metric stores you enable (it can be more than one).
 * `provider`: Configure, validate, and view the specified provider.
 * `security`: Configure Spinnaker's security. This includes external SSL, authentication mechanisms, and authorization policies.
 * `storage`: Show Spinnaker's persistent storage configuration.
 * `version`: Configure & view the current deployment of Spinnaker's version.

---
## hal config ci

Configure, validate, and view the specified Continuous Integration service.

#### Usage
```
hal config ci [subcommands]
```

#### Subcommands
 * `jenkins`: Manage and view Spinnaker configuration for the jenkins ci

---
## hal config ci jenkins

Manage and view Spinnaker configuration for the jenkins ci

#### Usage
```
hal config ci jenkins [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Set the jenkins ci as disabled
 * `enable`: Set the jenkins ci as enabled
 * `master`: Manage and view Spinnaker configuration for the jenkins Continuous Integration services's master

---
## hal config ci jenkins disable

Set the jenkins ci as disabled

#### Usage
```
hal config ci jenkins disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config ci jenkins enable

Set the jenkins ci as enabled

#### Usage
```
hal config ci jenkins enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config ci jenkins master

Manage and view Spinnaker configuration for the jenkins Continuous Integration services's master

#### Usage
```
hal config ci jenkins master MASTER [parameters] [subcommands]
```

#### Parameters
`MASTER`: The name of the master to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add a master for the jenkins Continuous Integration service.
 * `delete`: Delete a specific jenkins master by name.
 * `edit`: Edit a master for the jenkins Continuous Integration service.
 * `get`: Get the specified master details for jenkins.
 * `list`: List the master names for jenkins.

---
## hal config ci jenkins master add

Add a master for the jenkins Continuous Integration service.

#### Usage
```
hal config ci jenkins master add MASTER [parameters]
```

#### Parameters
`MASTER`: The name of the master to operate on.
 * `--address`: (*Required*) The address your jenkins master is reachable at.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--password`: (*Sensitive data* - user will be prompted on standard input) The password of the jenkins user to authenticate as.
 * `--username`: The username of the jenkins user to authenticate as.


---
## hal config ci jenkins master delete

Delete a specific jenkins master by name.

#### Usage
```
hal config ci jenkins master delete MASTER [parameters]
```

#### Parameters
`MASTER`: The name of the master to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config ci jenkins master edit

Edit a master for the jenkins Continuous Integration service.

#### Usage
```
hal config ci jenkins master edit MASTER [parameters]
```

#### Parameters
`MASTER`: The name of the master to operate on.
 * `--address`: The address your jenkins master is reachable at.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--password`: (*Sensitive data* - user will be prompted on standard input) The password of the jenkins user to authenticate as.
 * `--username`: The username of the jenkins user to authenticate as.


---
## hal config ci jenkins master get

Get the specified master details for jenkins.

#### Usage
```
hal config ci jenkins master get MASTER [parameters]
```

#### Parameters
`MASTER`: The name of the master to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config ci jenkins master list

List the master names for jenkins.

#### Usage
```
hal config ci jenkins master list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config deploy

Display the configured Spinnaker deployment.

#### Usage
```
hal config deploy [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Edit Spinnaker's deployment footprint and configuration.

---
## hal config deploy edit

Edit Spinnaker's deployment footprint and configuration.

#### Usage
```
hal config deploy edit [parameters]
```

#### Parameters
 * `--account-name`: The Spinnaker account that Spinnaker will be deployed to, assuming you are runninga deployment of Spinnaker that requires an active cloud provider.
 * `--consul-address`: The address of a running Consul cluster. See https://www.consul.io/.
This is only required when Spinnaker is being deployed in non-Kubernetes clustered configuration.
 * `--consul-enabled`: Whether or not to use Consul as a service discovery mechanism to deploy Spinnaker.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--type`: Flotilla: Deploy Spinnaker with one server group per microservice, and a single shared Redis.
LocalhostDebian: Download and run the Spinnaker debians on the machine running the Daemon.
 * `--vault-address`: The address of a running Vault datastore. See https://www.vaultproject.io/.This is only required when Spinnaker is being deployed in non-Kubernetes clustered configuration.
 * `--vault-enabled`: Whether or not to use Vault as a secret storage mechanism to deploy Spinnaker.


---
## hal config features

Display the state of Spinnaker's feature flags.

#### Usage
```
hal config features [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Enable and disable Spinnaker feature flags.

---
## hal config features edit

Enable and disable Spinnaker feature flags.

#### Usage
```
hal config features edit [parameters]
```

#### Parameters
 * `--chaos`: Enable Chaos Monkey support. For this to work, you'll need a running Chaos Monkey deployment. Currently, Halyard doesn't configure Chaos Monkey for you; read more instructions here https://github.com/Netflix/chaosmonkey/wiki.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--jobs`: Allow Spinnaker to run containers in Kubernetes and Titus as Job stages in pipelines.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config generate

Generate the full Spinnaker config for your current deployment.

#### Usage
```
hal config generate [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config metric-stores

Configure Spinnaker's metric stores. This configuration only affects the publishing of metrics against whichever metric stores you enable (it can be more than one).

#### Usage
```
hal config metric-stores [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `datadog`: Configure your datadog metric store.
 * `edit`: Configure global metric stores properties.
 * `prometheus`: Configure your prometheus metric store.
 * `stackdriver`: Configure your stackdriver metric store.

---
## hal config metric-stores datadog

Configure your datadog metric store.

#### Usage
```
hal config metric-stores datadog [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Set the datadog method as disabled
 * `edit`: Edit the datadog authentication method.
 * `enable`: Set the datadog method as enabled

---
## hal config metric-stores datadog disable

Set the datadog method as disabled

#### Usage
```
hal config metric-stores datadog disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config metric-stores datadog edit

Edit the datadog authentication method.

#### Usage
```
hal config metric-stores datadog edit [parameters]
```

#### Parameters
 * `--api-key`: Your datadog API key.
 * `--app-key`: Your datadog app key. This is only required if you want Spinnaker to push pre-configured Spinnaker dashboards to your Datadog account.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config metric-stores datadog enable

Set the datadog method as enabled

#### Usage
```
hal config metric-stores datadog enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config metric-stores edit

Configure global metric stores properties.

#### Usage
```
hal config metric-stores edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--period`: (*Required*) Set the polling period for the monitoring daemon.


---
## hal config metric-stores prometheus

Configure your prometheus metric store.

#### Usage
```
hal config metric-stores prometheus [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Set the prometheus method as disabled
 * `edit`: Edit the prometheus authentication method.
 * `enable`: Set the prometheus method as enabled

---
## hal config metric-stores prometheus disable

Set the prometheus method as disabled

#### Usage
```
hal config metric-stores prometheus disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config metric-stores prometheus edit

Edit the prometheus authentication method.

#### Usage
```
hal config metric-stores prometheus edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--push-gateway`: The endpoint the monitoring Daemon should push metrics to. If you have configured Prometheus to automatically discover all your Spinnaker services and pull metrics from them this is not required.


---
## hal config metric-stores prometheus enable

Set the prometheus method as enabled

#### Usage
```
hal config metric-stores prometheus enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config metric-stores stackdriver

Configure your stackdriver metric store.

#### Usage
```
hal config metric-stores stackdriver [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Set the stackdriver method as disabled
 * `edit`: Edit the stackdriver authentication method.
 * `enable`: Set the stackdriver method as enabled

---
## hal config metric-stores stackdriver disable

Set the stackdriver method as disabled

#### Usage
```
hal config metric-stores stackdriver disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config metric-stores stackdriver edit

Edit the stackdriver authentication method.

#### Usage
```
hal config metric-stores stackdriver edit [parameters]
```

#### Parameters
 * `--credentials-path`: A path to a Google JSON service account that has permission to publish metrics.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--project`: The project Spinnaker's metrics should be published to.
 * `--zone`: The zone Spinnaker's metrics should be associated with.


---
## hal config metric-stores stackdriver enable

Set the stackdriver method as enabled

#### Usage
```
hal config metric-stores stackdriver enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider

Configure, validate, and view the specified provider.

#### Usage
```
hal config provider [subcommands]
```

#### Subcommands
 * `appengine`: Manage and view Spinnaker configuration for the appengine provider
 * `aws`: Manage and view Spinnaker configuration for the aws provider
 * `azure`: Manage and view Spinnaker configuration for the azure provider
 * `dcos`: Manage and view Spinnaker configuration for the dcos provider
 * `docker-registry`: Manage and view Spinnaker configuration for the dockerRegistry provider
 * `google`: Manage and view Spinnaker configuration for the google provider
 * `kubernetes`: Manage and view Spinnaker configuration for the kubernetes provider
 * `openstack`: Manage and view Spinnaker configuration for the openstack provider
 * `oraclebmcs`: Manage and view Spinnaker configuration for the oraclebmcs provider

---
## hal config provider appengine

The App Engine provider is used to deploy resources to any number of App Engine applications. To get started with App Engine, visit https://cloud.google.com/appengine/docs/. For more information on how to configure individual accounts, please read the documentation under `hal config provider appengine account -h`.

#### Usage
```
hal config provider appengine [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the appengine provider's account
 * `disable`: Set the appengine provider as disabled
 * `enable`: Set the appengine provider as enabled

---
## hal config provider appengine account

An account in the App Engine provider refers to a single App Engine application. Spinnaker assumes that your App Engine application already exists. You can create an application in your Google Cloud Platform project by running `gcloud app create --region <region>`.

#### Usage
```
hal config provider appengine account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the appengine provider.
 * `delete`: Delete a specific appengine account by name.
 * `edit`: Edit an account in the appengine provider.
 * `get`: Get the specified account details for the appengine provider.
 * `list`: List the account names for the appengine provider.

---
## hal config provider appengine account add

Add an account to the appengine provider.

#### Usage
```
hal config provider appengine account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--git-https-password`: (*Sensitive data* - user will be prompted on standard input) A password to be used when connecting with a remote git repository server over HTTPS.
 * `--git-https-username`: A username to be used when connecting with a remote git repository server over HTTPS.
 * `--github-oauth-access-token`: (*Sensitive data* - user will be prompted on standard input) An OAuth token provided by Github for connecting to  a git repository over HTTPS. See https://help.github.com/articles/creating-an-access-token-for-command-line-use for more information.
 * `--json-path`: The path to a JSON service account that Spinnaker will use as credentials. This is only needed if Spinnaker is not deployed on a Google Compute Engine VM, or needs permissions not afforded to the VM it is running on. See https://cloud.google.com/compute/docs/access/service-accounts for more information.
 * `--local-repository-directory`: (*Default*: `/var/tmp/clouddriver`) A local directory to be used to stage source files for App Engine deployments within Spinnaker's Clouddriver microservice.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--project`: (*Required*) The Google Cloud Platform project this Spinnaker account will manage.
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--ssh-known-hosts-file-path`: The path to a known_hosts file to be used when connecting with a remote git repository over SSH.
 * `--ssh-private-key-file-path`: The path to an SSH private key to be used when connecting with a remote git repository over SSH.
 * `--ssh-private-key-passphrase`: (*Sensitive data* - user will be prompted on standard input) The passphrase to an SSH private key to be used when connecting with a remote git repository over SSH.
 * `--ssh-trust-unknown-hosts`: (*Default*: `false`) Enabling this flag will allow Spinnaker to connect with a remote git repository over SSH without verifying the server's IP address against a known_hosts file.


---
## hal config provider appengine account delete

Delete a specific appengine account by name.

#### Usage
```
hal config provider appengine account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider appengine account edit

Edit an account in the appengine provider.

#### Usage
```
hal config provider appengine account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--git-https-password`: (*Sensitive data* - user will be prompted on standard input) A password to be used when connecting with a remote git repository server over HTTPS.
 * `--git-https-username`: A username to be used when connecting with a remote git repository server over HTTPS.
 * `--github-oauth-access-token`: (*Sensitive data* - user will be prompted on standard input) An OAuth token provided by Github for connecting to  a git repository over HTTPS. See https://help.github.com/articles/creating-an-access-token-for-command-line-use for more information.
 * `--json-path`: The path to a JSON service account that Spinnaker will use as credentials. This is only needed if Spinnaker is not deployed on a Google Compute Engine VM, or needs permissions not afforded to the VM it is running on. See https://cloud.google.com/compute/docs/access/service-accounts for more information.
 * `--local-repository-directory`: A local directory to be used to stage source files for App Engine deployments within Spinnaker's Clouddriver microservice.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--project`: The Google Cloud Platform project this Spinnaker account will manage.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--ssh-known-hosts-file-path`: The path to a known_hosts file to be used when connecting with a remote git repository over SSH.
 * `--ssh-private-key-file-path`: The path to an SSH private key to be used when connecting with a remote git repository over SSH.
 * `--ssh-private-key-passphrase`: (*Sensitive data* - user will be prompted on standard input) The passphrase to an SSH private key to be used when connecting with a remote git repository over SSH.
 * `--ssh-trust-unknown-hosts`: Enabling this flag will allow Spinnaker to connect with a remote git repository over SSH without verifying the server's IP address against a known_hosts file.


---
## hal config provider appengine account get

Get the specified account details for the appengine provider.

#### Usage
```
hal config provider appengine account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider appengine account list

List the account names for the appengine provider.

#### Usage
```
hal config provider appengine account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider appengine disable

Set the appengine provider as disabled

#### Usage
```
hal config provider appengine disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider appengine enable

Set the appengine provider as enabled

#### Usage
```
hal config provider appengine enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider aws

Manage and view Spinnaker configuration for the aws provider

#### Usage
```
hal config provider aws [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the aws provider's account
 * `disable`: Set the aws provider as disabled
 * `edit`: Set provider-wide properties for the AWS provider
 * `enable`: Set the aws provider as enabled

---
## hal config provider aws account

Manage and view Spinnaker configuration for the aws provider's account

#### Usage
```
hal config provider aws account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the aws provider.
 * `delete`: Delete a specific aws account by name.
 * `edit`: Edit an account in the aws provider.
 * `get`: Get the specified account details for the aws provider.
 * `list`: List the account names for the aws provider.

---
## hal config provider aws account add

Add an account to the aws provider.

#### Usage
```
hal config provider aws account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--account-id`: (*Required*) Your AWS account ID to manage. See http://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html for more information.
 * `--assume-role`: (*Required*) If set, Halyard will configure a credentials provider that uses AWS Security Token Service to assume the specified role.

Example: "user/spinnaker" or "role/spinnakerManaged"
 * `--default-key-pair`: Provide the name of the AWS key-pair to use. See http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html for more information.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--discovery`: The endpoint your Eureka discovery system is reachable at. See https://github.com/Netflix/eureka for more information.

Example: http://{{region}}.eureka.url.to.use:8080/eureka-server/v2 

Using {{region}} will make Spinnaker use AWS regions in the hostname to access discovery so that you can have discovery for multiple regions.
 * `--edda`: The endpoint Edda is reachable at. Edda is not a hard dependency of Spinnaker, but is helpful for reducing the request volume against AWS. See https://github.com/Netflix/edda for more information.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--regions`: (*Default*: `[]`) The AWS regions this Spinnaker account will manage.
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.


---
## hal config provider aws account delete

Delete a specific aws account by name.

#### Usage
```
hal config provider aws account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider aws account edit

Edit an account in the aws provider.

#### Usage
```
hal config provider aws account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--account-id`: Your AWS account ID to manage. See http://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html for more information.
 * `--add-region`: Add this region to the list of managed regions.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--assume-role`: If set, Halyard will configure a credentials provider that uses AWS Security Token Service to assume the specified role.

Example: "user/spinnaker" or "role/spinnakerManaged"
 * `--default-key-pair`: Provide the name of the AWS key-pair to use. See http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html for more information.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--discovery`: The endpoint your Eureka discovery system is reachable at. See https://github.com/Netflix/eureka for more information.

Example: http://{{region}}.eureka.url.to.use:8080/eureka-server/v2 

Using {{region}} will make Spinnaker use AWS regions in the hostname to access discovery so that you can have discovery for multiple regions.
 * `--edda`: The endpoint Edda is reachable at. Edda is not a hard dependency of Spinnaker, but is helpful for reducing the request volume against AWS. See https://github.com/Netflix/edda for more information.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--regions`: The AWS regions this Spinnaker account will manage.
 * `--remove-region`: Remove this region from the list of managed regions.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.


---
## hal config provider aws account get

Get the specified account details for the aws provider.

#### Usage
```
hal config provider aws account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider aws account list

List the account names for the aws provider.

#### Usage
```
hal config provider aws account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider aws disable

Set the aws provider as disabled

#### Usage
```
hal config provider aws disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider aws edit

The AWS provider requires a central "Managing Account" to authenticate on behalf of other AWS accounts, or act as your sole, credential-based account. Since this configuration, as well as some defaults, span all AWS accounts, it is generally required to edit the AWS provider using this command.

#### Usage
```
hal config provider aws edit [parameters]
```

#### Parameters
 * `--access-key-id`: Your AWS Access Key ID. If not provided, Halyard/Spinnaker will try to find AWS credentials as described at http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--secret-access-key`: (*Sensitive data* - user will be prompted on standard input) Your AWS Secret Key.


---
## hal config provider aws enable

Set the aws provider as enabled

#### Usage
```
hal config provider aws enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure

Manage and view Spinnaker configuration for the azure provider

#### Usage
```
hal config provider azure [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the azure provider's account
 * `bakery`: Manage and view Spinnaker configuration for the azure provider's image bakery configuration.
 * `disable`: Set the azure provider as disabled
 * `enable`: Set the azure provider as enabled

---
## hal config provider azure account

Manage and view Spinnaker configuration for the azure provider's account

#### Usage
```
hal config provider azure account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the azure provider.
 * `delete`: Delete a specific azure account by name.
 * `edit`: Edit an account in the azure provider.
 * `get`: Get the specified account details for the azure provider.
 * `list`: List the account names for the azure provider.

---
## hal config provider azure account add

Add an account to the azure provider.

#### Usage
```
hal config provider azure account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--app-key`: (*Required*) (*Sensitive data* - user will be prompted on standard input) The appKey (password) of your service principal.
 * `--client-id`: (*Required*) The clientId (also called appId) of your service principal.
 * `--default-key-vault`: (*Required*) The name of a KeyVault that contains the default user name and password used to create VMs
 * `--default-resource-group`: (*Required*) The default resource group to contain any non-application specific resources.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--object-id`: The objectId of your service principal. This is only required if using Packer to bake Windows images.
 * `--packer-resource-group`: The resource group to use if baking images with Packer.
 * `--packer-storage-account`: The storage account to use if baking images with Packer.
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--subscription-id`: (*Required*) The subscriptionId that your service principal is assigned to.
 * `--tenant-id`: (*Required*) The tenantId that your service principal is assigned to.


---
## hal config provider azure account delete

Delete a specific azure account by name.

#### Usage
```
hal config provider azure account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure account edit

Edit an account in the azure provider.

#### Usage
```
hal config provider azure account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--app-key`: (*Sensitive data* - user will be prompted on standard input) The appKey (password) of your service principal.
 * `--client-id`: The clientId (also called appId) of your service principal.
 * `--default-key-vault`: The name of a KeyVault that contains the default user name and password used to create VMs
 * `--default-resource-group`: The default resource group to contain any non-application specific resources.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--object-id`: The objectId of your service principal. This is only required if using Packer to bake Windows images.
 * `--packer-resource-group`: The resource group to use if baking images with Packer.
 * `--packer-storage-account`: The storage account to use if baking images with Packer.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--subscription-id`: The subscriptionId that your service principal is assigned to.
 * `--tenant-id`: The tenantId that your service principal is assigned to.


---
## hal config provider azure account get

Get the specified account details for the azure provider.

#### Usage
```
hal config provider azure account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure account list

List the account names for the azure provider.

#### Usage
```
hal config provider azure account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure bakery

Manage and view Spinnaker configuration for the azure provider's image bakery configuration.

#### Usage
```
hal config provider azure bakery [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `base-image`: Manage and view Spinnaker configuration for the azure provider's base image.
 * `edit`: Edit the azure provider's bakery default options.

---
## hal config provider azure bakery base-image

Manage and view Spinnaker configuration for the azure provider's base image.

#### Usage
```
hal config provider azure bakery base-image [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add a base image for the azure provider's bakery.
 * `delete`: Delete a specific azure base image by name.
 * `edit`: Edit a base image for the azure provider's bakery.
 * `get`: Get the specified base image details for the azure provider.
 * `list`: List the base image names for the azure provider.

---
## hal config provider azure bakery base-image add

Add a base image for the azure provider's bakery.

#### Usage
```
hal config provider azure bakery base-image add BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--detailed-description`: A long description to help human operators identify the image.
 * `--image-version`: The version of your base image. This defaults to 'latest' if not specified.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--offer`: (*Required*) The offer for your base image. See https://aka.ms/azspinimage to get a list of images.
 * `--package-type`: This is used to help Spinnaker's bakery download the build artifacts you supply it with. For example, specifying 'deb' indicates that your artifacts will need to be fetched from a debian repository.
 * `--publisher`: (*Required*) The Publisher name for your base image. See https://aka.ms/azspinimage to get a list of images.
 * `--short-description`: A short description to help human operators identify the image.
 * `--sku`: (*Required*) The SKU for your base image. See https://aka.ms/azspinimage to get a list of images.
 * `--template-file`: This is the name of the packer template that will be used to bake images from this base image. The template file must be found in this list https://github.com/spinnaker/rosco/tree/master/rosco-web/config/packer, or supplied as described here: https://spinnaker.io/setup/bakery/


---
## hal config provider azure bakery base-image delete

Delete a specific azure base image by name.

#### Usage
```
hal config provider azure bakery base-image delete BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure bakery base-image edit

Edit a base image for the azure provider's bakery.

#### Usage
```
hal config provider azure bakery base-image edit BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--detailed-description`: A long description to help human operators identify the image.
 * `--id`: This is the identifier used by your cloud to find this base image.
 * `--image-version`: The version of your base image. This defaults to 'latest' if not specified.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--offer`: The offer for your base image. See https://aka.ms/azspinimage to get a list of images.
 * `--package-type`: This is used to help Spinnaker's bakery download the build artifacts you supply it with. For example, specifying 'deb' indicates that your artifacts will need to be fetched from a debian repository.
 * `--publisher`: The Publisher name for your base image. See https://aka.ms/azspinimage to get a list of images.
 * `--short-description`: A short description to help human operators identify the image.
 * `--sku`: The SKU for your base image. See https://aka.ms/azspinimage to get a list of images.
 * `--template-file`: This is the name of the packer template that will be used to bake images from this base image. The template file must be found in this list https://github.com/spinnaker/rosco/tree/master/rosco-web/config/packer, or supplied as described here: https://spinnaker.io/setup/bakery/


---
## hal config provider azure bakery base-image get

Get the specified base image details for the azure provider.

#### Usage
```
hal config provider azure bakery base-image get BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure bakery base-image list

List the base image names for the azure provider.

#### Usage
```
hal config provider azure bakery base-image list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure bakery edit

Edit the azure provider's bakery default options.

#### Usage
```
hal config provider azure bakery edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure disable

Set the azure provider as disabled

#### Usage
```
hal config provider azure disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider azure enable

Set the azure provider as enabled

#### Usage
```
hal config provider azure enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos

Manage and view Spinnaker configuration for the dcos provider

#### Usage
```
hal config provider dcos [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the dcos provider's account
 * `cluster`: Manage and view Spinnaker configuration for the dcos provider's cluster
 * `disable`: Set the dcos provider as disabled
 * `enable`: Set the dcos provider as enabled

---
## hal config provider dcos account

Manage and view Spinnaker configuration for the dcos provider's account

#### Usage
```
hal config provider dcos account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the dcos provider.
 * `delete`: Delete a specific dcos account by name.
 * `edit`: Edit an account in the dcos provider.
 * `get`: Get the specified account details for the dcos provider.
 * `list`: List the account names for the dcos provider.

---
## hal config provider dcos account add

Add an account to the dcos provider.

#### Usage
```
hal config provider dcos account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--cluster`: (*Required*) Reference to the name of the cluster from the set of clusters defined for this provider
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--docker-registries`: (*Default*: `[]`) (*Required*) Provide the list of docker registries to use with this DC/OS account
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--password`: Password for a user account
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--service-key`: Secret key for service account authentication
 * `--uid`: (*Required*) User or service account identifier


---
## hal config provider dcos account delete

Delete a specific dcos account by name.

#### Usage
```
hal config provider dcos account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos account edit

Edit an account in the dcos provider.

#### Usage
```
hal config provider dcos account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-docker-registry`: Add this docker registry to the list of docker registries to use as a source of images.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--docker-registries`: (*Default*: `[]`) Provide the list of docker registries to use with this DC/OS account
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--remove-credential`: (*Default*: `[]`) Provide the cluster name and uid of credentials to remove: --remove-credential my-cluster my-user
 * `--remove-docker-registry`: Remove this docker registry from the list of docker registries to use as a source of images.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--update-service-credential`: (*Default*: `[]`) A DC/OS cluster service account credential in 3 parts: cluster-name uid serviceKey
 * `--update-user-credential`: (*Default*: `[]`) A DC/OS cluster user credential in 3 parts: cluster-name uid password


---
## hal config provider dcos account get

Get the specified account details for the dcos provider.

#### Usage
```
hal config provider dcos account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos account list

List the account names for the dcos provider.

#### Usage
```
hal config provider dcos account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos cluster

Manage and view Spinnaker configuration for the dcos provider's cluster

#### Usage
```
hal config provider dcos cluster CLUSTER [parameters] [subcommands]
```

#### Parameters
`CLUSTER`: The name of the cluster to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Manage and view Spinnaker configuration for the dcos provider's cluster
 * `delete`: Delete a specific dcos cluster by name.
 * `edit`: Manage and view Spinnaker configuration for the dcos provider's cluster
 * `get`: Get the specified cluster details for the dcos provider.
 * `list`: List the cluster names for the dcos provider.

---
## hal config provider dcos cluster add

Manage and view Spinnaker configuration for the dcos provider's cluster

#### Usage
```
hal config provider dcos cluster add CLUSTER [parameters]
```

#### Parameters
`CLUSTER`: The name of the cluster to operate on.
 * `--ca-cert-data`: Root certificate to trust for connections to the cluster
 * `--dcos-url`: (*Required*) URL of the endpoint for the DC/OS cluster's admin router.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--lb-account-secret`: Name of the secret to use for allowing marathon-lb to authenticate with the cluster.  Only necessary for clusters with strict or permissive security.
 * `--lb-image`: Marathon-lb image to use when creating a load balancer with Spinnaker
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--skip-tls-verify`: Set this flag to disable verification of certificates from the cluster (insecure)


---
## hal config provider dcos cluster delete

Delete a specific dcos cluster by name.

#### Usage
```
hal config provider dcos cluster delete CLUSTER [parameters]
```

#### Parameters
`CLUSTER`: The name of the cluster to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos cluster edit

Manage and view Spinnaker configuration for the dcos provider's cluster

#### Usage
```
hal config provider dcos cluster edit CLUSTER [parameters]
```

#### Parameters
`CLUSTER`: The name of the cluster to operate on.
 * `--ca-cert-data`: Root certificate to trust for connections to the cluster
 * `--dcos-url`: URL of the endpoint for the DC/OS cluster's admin router.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--lb-account-secret`: Name of the secret to use for allowing marathon-lb to authenticate with the cluster.  Only necessary for clusters with strict or permissive security.
 * `--lb-image`: Marathon-lb image to use when creating a load balancer with Spinnaker
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--remove-ca-cert-data`: (*Default*: `false`) Remove the CA certificate for this cluster
 * `--remove-lb`: (*Default*: `false`) Remove the load balancer attributes for this cluster
 * `--skip-tls-verify`: Set this flag to disable verification of certificates from the cluster (insecure)


---
## hal config provider dcos cluster get

Get the specified cluster details for the dcos provider.

#### Usage
```
hal config provider dcos cluster get CLUSTER [parameters]
```

#### Parameters
`CLUSTER`: The name of the cluster to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos cluster list

List the cluster names for the dcos provider.

#### Usage
```
hal config provider dcos cluster list CLUSTER [parameters]
```

#### Parameters
`CLUSTER`: The name of the cluster to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos disable

Set the dcos provider as disabled

#### Usage
```
hal config provider dcos disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider dcos enable

Set the dcos provider as enabled

#### Usage
```
hal config provider dcos enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider docker-registry

Manage and view Spinnaker configuration for the dockerRegistry provider

#### Usage
```
hal config provider docker-registry [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the dockerRegistry provider's account
 * `disable`: Set the dockerRegistry provider as disabled
 * `enable`: Set the dockerRegistry provider as enabled

---
## hal config provider docker-registry account

Manage and view Spinnaker configuration for the dockerRegistry provider's account

#### Usage
```
hal config provider docker-registry account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the dockerRegistry provider.
 * `delete`: Delete a specific dockerRegistry account by name.
 * `edit`: Edit an account in the dockerRegistry provider.
 * `get`: Get the specified account details for the dockerRegistry provider.
 * `list`: List the account names for the dockerRegistry provider.

---
## hal config provider docker-registry account add

Add an account to the dockerRegistry provider.

#### Usage
```
hal config provider docker-registry account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--address`: (*Default*: `gcr.io`) (*Required*) The registry address you want to pull and deploy images from. For example:

  index.docker.io     - DockerHub
  quay.io             - Quay
  gcr.io              - Google Container Registry (GCR)
  [us|eu|asia].gcr.io - Regional GCR
  localhost           - Locally deployed registry
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--email`: (*Default*: `fake.email@spinnaker.io`) Your docker registry email (often this only needs to be well-formed, rather than be a real address)
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--password`: (*Sensitive data* - user will be prompted on standard input) Your docker registry password
 * `--password-file`: The path to a file containing your docker password in plaintext (not a docker/config.json file)
 * `--repositories`: (*Default*: `[]`) An optional list of repositories to cache images from. If not provided, Spinnaker will attempt to read accessible repositories from the registries _catalog endpoint
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--username`: Your docker registry username


---
## hal config provider docker-registry account delete

Delete a specific dockerRegistry account by name.

#### Usage
```
hal config provider docker-registry account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider docker-registry account edit

Edit an account in the dockerRegistry provider.

#### Usage
```
hal config provider docker-registry account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-repository`: Add this repository to the list of repositories to cache images from.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--address`: The registry address you want to pull and deploy images from. For example:

  index.docker.io     - DockerHub
  quay.io             - Quay
  gcr.io              - Google Container Registry (GCR)
  [us|eu|asia].gcr.io - Regional GCR
  localhost           - Locally deployed registry
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--email`: Your docker registry email (often this only needs to be well-formed, rather than be a real address)
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--password`: (*Sensitive data* - user will be prompted on standard input) Your docker registry password
 * `--password-file`: The path to a file containing your docker password in plaintext (not a docker/config.json file)
 * `--remove-repository`: Remove this repository to the list of repositories to cache images from.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--repositories`: (*Default*: `[]`) An optional list of repositories to cache images from. If not provided, Spinnaker will attempt to read accessible repositories from the registries _catalog endpoint
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--username`: Your docker registry username


---
## hal config provider docker-registry account get

Get the specified account details for the dockerRegistry provider.

#### Usage
```
hal config provider docker-registry account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider docker-registry account list

List the account names for the dockerRegistry provider.

#### Usage
```
hal config provider docker-registry account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider docker-registry disable

Set the dockerRegistry provider as disabled

#### Usage
```
hal config provider docker-registry disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider docker-registry enable

Set the dockerRegistry provider as enabled

#### Usage
```
hal config provider docker-registry enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google

Manage and view Spinnaker configuration for the google provider

#### Usage
```
hal config provider google [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the google provider's account
 * `bakery`: Manage and view Spinnaker configuration for the google provider's image bakery configuration.
 * `disable`: Set the google provider as disabled
 * `enable`: Set the google provider as enabled

---
## hal config provider google account

Manage and view Spinnaker configuration for the google provider's account

#### Usage
```
hal config provider google account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the google provider.
 * `delete`: Delete a specific google account by name.
 * `edit`: Edit an account in the google provider.
 * `get`: Get the specified account details for the google provider.
 * `list`: List the account names for the google provider.

---
## hal config provider google account add

Add an account to the google provider.

#### Usage
```
hal config provider google account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--alpha-listed`: (*Default*: `false`) Enable this flag if your project has access to alpha features and you want Spinnaker to take advantage of them.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--image-projects`: (*Default*: `[]`) A list of Google Cloud Platform projects Spinnaker will be able to cache and deploy images from. When this is omitted, it defaults to the current project. Each project must have granted the IAM role `compute.imageUser` to the service account associated with the json key used by this account, as well as to the 'Google APIs service account' automatically created for the project being managed (should look similar to `12345678912@cloudservices.gserviceaccount.com`). See https://cloud.google.com/compute/docs/images/sharing-images-across-projects for more information about sharing images across GCP projects.
 * `--json-path`: The path to a JSON service account that Spinnaker will use as credentials. This is only needed if Spinnaker is not deployed on a Google Compute Engine VM, or needs permissions not afforded to the VM it is running on. See https://cloud.google.com/compute/docs/access/service-accounts for more information.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--project`: (*Required*) The Google Cloud Platform project this Spinnaker account will manage.
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--user-data`: The path to user data template file. Spinnaker has the ability to inject userdata into generated instance templates. The mechanism is via a template file that is token replaced to provide some specifics about the deployment. See https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/UserData.md for more information.


---
## hal config provider google account delete

Delete a specific google account by name.

#### Usage
```
hal config provider google account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google account edit

Edit an account in the google provider.

#### Usage
```
hal config provider google account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-image-project`: Add this image project to the list of image projects to cache and deploy images from.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--image-projects`: (*Default*: `[]`) A list of Google Cloud Platform projects Spinnaker will be able to cache and deploy images from. When this is omitted, it defaults to the current project. Each project must have granted the IAM role `compute.imageUser` to the service account associated with the json key used by this account, as well as to the 'Google APIs service account' automatically created for the project being managed (should look similar to `12345678912@cloudservices.gserviceaccount.com`). See https://cloud.google.com/compute/docs/images/sharing-images-across-projects for more information about sharing images across GCP projects.
 * `--json-path`: The path to a JSON service account that Spinnaker will use as credentials. This is only needed if Spinnaker is not deployed on a Google Compute Engine VM, or needs permissions not afforded to the VM it is running on. See https://cloud.google.com/compute/docs/access/service-accounts for more information.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--project`: The Google Cloud Platform project this Spinnaker account will manage.
 * `--remove-image-project`: Remove this image project from the list of image projects to cache and deploy images from.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--set-alpha-listed`: Enable this flag if your project has access to alpha features and you want Spinnaker to take advantage of them.
 * `--user-data`: The path to user data template file. Spinnaker has the ability to inject userdata into generated instance templates. The mechanism is via a template file that is token replaced to provide some specifics about the deployment. See https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/UserData.md for more information.


---
## hal config provider google account get

Get the specified account details for the google provider.

#### Usage
```
hal config provider google account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google account list

List the account names for the google provider.

#### Usage
```
hal config provider google account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google bakery

Manage and view Spinnaker configuration for the google provider's image bakery configuration.

#### Usage
```
hal config provider google bakery [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `base-image`: Manage and view Spinnaker configuration for the google provider's base image.
 * `edit`: Edit the google provider's bakery default options.

---
## hal config provider google bakery base-image

Manage and view Spinnaker configuration for the google provider's base image.

#### Usage
```
hal config provider google bakery base-image [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add a base image for the google provider's bakery.
 * `delete`: Delete a specific google base image by name.
 * `edit`: Edit a base image for the google provider's bakery.
 * `get`: Get the specified base image details for the google provider.
 * `list`: List the base image names for the google provider.

---
## hal config provider google bakery base-image add

Add a base image for the google provider's bakery.

#### Usage
```
hal config provider google bakery base-image add BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--detailed-description`: A long description to help human operators identify the image.
 * `--is-image-family`: (*Default*: `false`) todo(duftler) I couldn't find a description on the packer website of what this is.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--package-type`: This is used to help Spinnaker's bakery download the build artifacts you supply it with. For example, specifying 'deb' indicates that your artifacts will need to be fetched from a debian repository.
 * `--short-description`: A short description to help human operators identify the image.
 * `--source-image`: The source image. If both source image and source image family are set, source image will take precedence.
 * `--source-image-family`: The source image family to create the image from. The newest, non-deprecated image is used.
 * `--template-file`: This is the name of the packer template that will be used to bake images from this base image. The template file must be found in this list https://github.com/spinnaker/rosco/tree/master/rosco-web/config/packer, or supplied as described here: https://spinnaker.io/setup/bakery/


---
## hal config provider google bakery base-image delete

Delete a specific google base image by name.

#### Usage
```
hal config provider google bakery base-image delete BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google bakery base-image edit

Edit a base image for the google provider's bakery.

#### Usage
```
hal config provider google bakery base-image edit BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--detailed-description`: A long description to help human operators identify the image.
 * `--id`: This is the identifier used by your cloud to find this base image.
 * `--is-image-family`: todo(duftler) I couldn't find a description on the packer website of what this is.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--package-type`: This is used to help Spinnaker's bakery download the build artifacts you supply it with. For example, specifying 'deb' indicates that your artifacts will need to be fetched from a debian repository.
 * `--short-description`: A short description to help human operators identify the image.
 * `--source-image`: The source image. If both source image and source image family are set, source image will take precedence.
 * `--source-image-family`: The source image family to create the image from. The newest, non-deprecated image is used.
 * `--template-file`: This is the name of the packer template that will be used to bake images from this base image. The template file must be found in this list https://github.com/spinnaker/rosco/tree/master/rosco-web/config/packer, or supplied as described here: https://spinnaker.io/setup/bakery/


---
## hal config provider google bakery base-image get

Get the specified base image details for the google provider.

#### Usage
```
hal config provider google bakery base-image get BASE-IMAGE [parameters]
```

#### Parameters
`BASE-IMAGE`: The name of the base image to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google bakery base-image list

List the base image names for the google provider.

#### Usage
```
hal config provider google bakery base-image list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google bakery edit

Edit the google provider's bakery default options.

#### Usage
```
hal config provider google bakery edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--network`: Set the default network your images will be baked in.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--use-internal-ip`: Use the internal rather than external IP of the VM baking your image.
 * `--zone`: Set the default zone your images will be baked in.


---
## hal config provider google disable

Set the google provider as disabled

#### Usage
```
hal config provider google disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider google enable

Set the google provider as enabled

#### Usage
```
hal config provider google enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider kubernetes

The Kubernetes provider is used to deploy Kubernetes resources to any number of Kubernetes clusters. Spinnaker assumes you have a Kubernetes cluster already running. If you don't, you must configure one: https://kubernetes.io/docs/getting-started-guides/. 

Before proceeding, please visit https://kubernetes.io/docs/concepts/cluster-administration/authenticate-across-clusters-kubeconfig/to make sure you're familiar with the authentication terminology. For more information on how to configure individual accounts, or how to deploy to multiple clusters, please read the documentation under `hal config provider kubernetes account -h`.

#### Usage
```
hal config provider kubernetes [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the kubernetes provider's account
 * `disable`: Set the kubernetes provider as disabled
 * `edit`: Set provider-wide properties for the Kubernetes provider
 * `enable`: Set the kubernetes provider as enabled

---
## hal config provider kubernetes account

An account in the Kubernetes provider refers to a single Kubernetes context. In Kubernetes, a context is the combination of a Kubernetes cluster and some credentials. If no context is specified, the default context in in your kubeconfig is assumed.

You must also provide a set of Docker Registries for each account. Spinnaker will automatically upload that Registry's credentials to the specified Kubernetes cluster allowing you to deploy those images without further configuration.

#### Usage
```
hal config provider kubernetes account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the kubernetes provider.
 * `delete`: Delete a specific kubernetes account by name.
 * `edit`: Edit an account in the kubernetes provider.
 * `get`: Get the specified account details for the kubernetes provider.
 * `list`: List the account names for the kubernetes provider.

---
## hal config provider kubernetes account add

Add an account to the kubernetes provider.

#### Usage
```
hal config provider kubernetes account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--context`: The kubernetes context to be managed by Spinnaker. See http://kubernetes.io/docs/user-guide/kubeconfig-file/#context for more information.
When no context is configured for an account the 'current-context' in your kubeconfig is assumed.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--docker-registries`: (*Default*: `[]`) (*Required*) A list of the Spinnaker docker registry account names this Spinnaker account can use as image sources. These docker registry accounts must be registered in your halconfig before you can add them here.
 * `--kubeconfig-file`: The path to your kubeconfig file. By default, it will be under the Spinnaker user's home directory in the typical .kube/config location.
 * `--namespaces`: (*Default*: `[]`) A list of namespaces this Spinnaker account can deploy to and will cache.
When no namespaces are configured, this defaults to 'all namespaces'.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--omit-namespaces`: (*Default*: `[]`) A list of namespaces this Spinnaker account cannot deploy to or cache.
This can only be set when no --namespaces are provided.
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.


---
## hal config provider kubernetes account delete

Delete a specific kubernetes account by name.

#### Usage
```
hal config provider kubernetes account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider kubernetes account edit

Edit an account in the kubernetes provider.

#### Usage
```
hal config provider kubernetes account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-docker-registry`: Add this docker registry to the list of docker registries to use as a source of images.
 * `--add-namespace`: Add this namespace to the list of namespaces to manage.
 * `--add-omit-namespace`: Add this namespace to the list of namespaces to omit.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--all-namespaces`: (*Default*: `false`) Set the list of namespaces to cache and deploy to every namespace available to your supplied credentials.
 * `--clear-context`: (*Default*: `false`) Removes the currently configured context, defaulting to 'current-context' in your kubeconfig.See http://kubernetes.io/docs/user-guide/kubeconfig-file/#context for more information.
 * `--context`: The kubernetes context to be managed by Spinnaker. See http://kubernetes.io/docs/user-guide/kubeconfig-file/#context for more information.
When no context is configured for an account the 'current-context' in your kubeconfig is assumed.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--docker-registries`: (*Default*: `[]`) A list of the Spinnaker docker registry account names this Spinnaker account can use as image sources. These docker registry accounts must be registered in your halconfig before you can add them here.
 * `--kubeconfig-file`: The path to your kubeconfig file. By default, it will be under the Spinnaker user's home directory in the typical .kube/config location.
 * `--namespaces`: (*Default*: `[]`) A list of namespaces this Spinnaker account can deploy to and will cache.
When no namespaces are configured, this defaults to 'all namespaces'.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--omit-namespaces`: (*Default*: `[]`) A list of namespaces this Spinnaker account cannot deploy to or cache.
This can only be set when no --namespaces are provided.
 * `--remove-docker-registry`: Remove this docker registry from the list of docker registries to use as a source of images.
 * `--remove-namespace`: Remove this namespace to the list of namespaces to manage.
 * `--remove-omit-namespace`: Remove this namespace to the list of namespaces to omit.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.


---
## hal config provider kubernetes account get

Get the specified account details for the kubernetes provider.

#### Usage
```
hal config provider kubernetes account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider kubernetes account list

List the account names for the kubernetes provider.

#### Usage
```
hal config provider kubernetes account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider kubernetes disable

Set the kubernetes provider as disabled

#### Usage
```
hal config provider kubernetes disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider kubernetes edit

Due to how the Kubenretes provider shards its cache resources, there is opportunity to tune how its caching should be handled. This command exists to allow you tune this caching behavior.

#### Usage
```
hal config provider kubernetes edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider kubernetes enable

Set the kubernetes provider as enabled

#### Usage
```
hal config provider kubernetes enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider openstack

Manage and view Spinnaker configuration for the openstack provider

#### Usage
```
hal config provider openstack [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the openstack provider's account
 * `disable`: Set the openstack provider as disabled
 * `enable`: Set the openstack provider as enabled

---
## hal config provider openstack account

Manage and view Spinnaker configuration for the openstack provider's account

#### Usage
```
hal config provider openstack account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the openstack provider.
 * `delete`: Delete a specific openstack account by name.
 * `edit`: Edit an account in the openstack provider.
 * `get`: Get the specified account details for the openstack provider.
 * `list`: List the account names for the openstack provider.

---
## hal config provider openstack account add

Add an account to the openstack provider.

#### Usage
```
hal config provider openstack account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--auth-url`: (*Required*) The auth url of your cloud, usually found in the Horizon console under Compute > Access & Security > API Access > url for Identity. Must be Keystone v3
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--domain-name`: (*Required*) The domain of the cloud. Can be found in the RC file.
 * `--insecure`: (*Default*: `false`) Disable certificate validation on SSL connections. Needed if certificates are self signed. Default false.
 * `--lbaas-poll-interval`: Interval in seconds to poll octavia when an entity is created, updated, or deleted. Default 5.
 * `--lbaas-poll-timout`: Time to stop polling octavia when a status of an entity does not change. Default 60.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--password`: (*Required*) The password used to access your cloud.
 * `--project-name`: (*Required*) The name of the project (formerly tenant) within the cloud. Can be found in the RC file.
 * `--regions`: (*Default*: `[]`) (*Required*) The region(s) of the cloud. Can be found in the RC file.
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--user-data-file`: User data passed to Heat Orchestration Template. Replacement of tokens supported, see http://www.spinnaker.io/v1.0/docs/target-deployment-configuration#section-openstack for details.
 * `--username`: (*Required*) The username used to access your cloud.


---
## hal config provider openstack account delete

Delete a specific openstack account by name.

#### Usage
```
hal config provider openstack account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider openstack account edit

Edit an account in the openstack provider.

#### Usage
```
hal config provider openstack account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-region`: Add this region to the list of managed regions.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--auth-url`: The auth url of your cloud, usually found in the Horizon console under Compute > Access & Security > API Access > url for Identity. Must be Keystone v3
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--domain-name`: The domain of the cloud. Can be found in the RC file.
 * `--insecure`: Disable certificate validation on SSL connections. Needed if certificates are self signed. Default false.
 * `--lbaas-poll-interval`: Interval in seconds to poll octavia when an entity is created, updated, or deleted. Default 5.
 * `--lbaas-poll-timout`: Time to stop polling octavia when a status of an entity does not change. Default 60.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--password`: The password used to access your cloud.
 * `--project-name`: The name of the project (formerly tenant) within the cloud. Can be found in the RC file.
 * `--regions`: (*Default*: `[]`) The region(s) of the cloud. Can be found in the RC file.
 * `--remove-region`: Remove this region from the list of managed regions.
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--remove-user-data-file`: (*Default*: `false`) Removes currently configured user data file.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--user-data-file`: User data passed to Heat Orchestration Template. Replacement of tokens supported, see http://www.spinnaker.io/v1.0/docs/target-deployment-configuration#section-openstack for details.
 * `--username`: The username used to access your cloud.


---
## hal config provider openstack account get

Get the specified account details for the openstack provider.

#### Usage
```
hal config provider openstack account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider openstack account list

List the account names for the openstack provider.

#### Usage
```
hal config provider openstack account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider openstack disable

Set the openstack provider as disabled

#### Usage
```
hal config provider openstack disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider openstack enable

Set the openstack provider as enabled

#### Usage
```
hal config provider openstack enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider oraclebmcs

Manage and view Spinnaker configuration for the oraclebmcs provider

#### Usage
```
hal config provider oraclebmcs [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `account`: Manage and view Spinnaker configuration for the oraclebmcs provider's account
 * `disable`: Set the oraclebmcs provider as disabled
 * `enable`: Set the oraclebmcs provider as enabled

---
## hal config provider oraclebmcs account

Manage and view Spinnaker configuration for the oraclebmcs provider's account

#### Usage
```
hal config provider oraclebmcs account ACCOUNT [parameters] [subcommands]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `add`: Add an account to the oraclebmcs provider.
 * `delete`: Delete a specific oraclebmcs account by name.
 * `edit`: Edit an account in the oraclebmcs provider.
 * `get`: Get the specified account details for the oraclebmcs provider.
 * `list`: List the account names for the oraclebmcs provider.

---
## hal config provider oraclebmcs account add

Add an account to the oraclebmcs provider.

#### Usage
```
hal config provider oraclebmcs account add ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--compartment-id`: (*Required*) Provide the OCID of the Oracle BMCS Compartment to use.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--fingerprint`: (*Required*) Fingerprint of the public key
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--region`: (*Required*) An Oracle BMCS region (e.g., us-phoenix-1)
 * `--required-group-membership`: (*Default*: `[]`) A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--ssh-private-key-file-path`: (*Required*) Path to the private key in PEM format
 * `--tenancyId`: (*Required*) Provide the OCID of the Oracle BMCS Tenancy to use.
 * `--user-id`: (*Required*) Provide the OCID of the Oracle BMCS User you're authenticating as


---
## hal config provider oraclebmcs account delete

Delete a specific oraclebmcs account by name.

#### Usage
```
hal config provider oraclebmcs account delete ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider oraclebmcs account edit

Edit an account in the oraclebmcs provider.

#### Usage
```
hal config provider oraclebmcs account edit ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--add-required-group-membership`: Add this group to the list of required group memberships.
 * `--compartment-id`: Provide the OCID of the Oracle BMCS Compartment to use.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--fingerprint`: Fingerprint of the public key
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--region`: An Oracle BMCS region (e.g., us-phoenix-1)
 * `--remove-required-group-membership`: Remove this group from the list of required group memberships.
 * `--required-group-membership`: A user must be a member of at least one specified group in order to make changes to this account's cloud resources.
 * `--ssh-private-key-file-path`: Path to the private key in PEM format
 * `--tenancyId`: Provide the OCID of the Oracle BMCS Tenancy to use.
 * `--user-id`: Provide the OCID of the Oracle BMCS User you're authenticating as


---
## hal config provider oraclebmcs account get

Get the specified account details for the oraclebmcs provider.

#### Usage
```
hal config provider oraclebmcs account get ACCOUNT [parameters]
```

#### Parameters
`ACCOUNT`: The name of the account to operate on.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider oraclebmcs account list

List the account names for the oraclebmcs provider.

#### Usage
```
hal config provider oraclebmcs account list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider oraclebmcs disable

Set the oraclebmcs provider as disabled

#### Usage
```
hal config provider oraclebmcs disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config provider oraclebmcs enable

Set the oraclebmcs provider as enabled

#### Usage
```
hal config provider oraclebmcs enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security

Configure Spinnaker's security. This includes external SSL, authentication mechanisms, and authorization policies.

#### Usage
```
hal config security [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `api`: Configure and view the API server's addressable URL and CORS policies.
 * `authn`: Configure your authentication settings for Spinnaker.
 * `authz`: Configure your authorization settings for Spinnaker.
 * `ui`: Configure and view the UI server's addressable URL.

---
## hal config security api

Configure and view the API server's addressable URL and CORS policies.

#### Usage
```
hal config security api [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Configure access policies specific to Spinnaker's API server.
 * `ssl`: Configure and view SSL settings for Spinnaker's API gateway.

---
## hal config security api edit

When Spinnaker is deployed to a remote host, the API server may be configured to accept auth requests from alternate sources, do SSL termination, or sit behind an externally configured proxy server or load balancer.

#### Usage
```
hal config security api edit [parameters]
```

#### Parameters
 * `--cors-access-pattern`: If you have authentication enabled, are accessing Spinnaker remotely, and are logging in from sources other than the UI, provide a regex matching all URLs authentication redirects may come from.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--override-base-url`: If you are accessing the API server remotely, provide the full base URL of whatever proxy or load balancer is fronting the API requests.


---
## hal config security api ssl

If you want the API server to do SSL termination, it must be enabled and configured here. If you are doing your own SSL termination, leave this disabled.

#### Usage
```
hal config security api ssl [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Disable SSL for the API gateway.
 * `edit`: Edit SSL settings for your API server.
 * `enable`: Enable SSL for the API gateway.

---
## hal config security api ssl disable

Disable SSL for the API gateway.

#### Usage
```
hal config security api ssl disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security api ssl edit

Configure SSL termination to handled by the API server's Tomcat server.

#### Usage
```
hal config security api ssl edit [parameters]
```

#### Parameters
 * `--client-auth`: (*Sensitive data* - user will be prompted on standard input) Declare 'WANT' when client auth is wanted but not mandatory, or 'NEED', when client auth is mandatory.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--key-alias`: Name of your keystore entry as generated with your keytool.
 * `--keystore`: Path to the keystore holding your security certificates.
 * `--keystore-password`: (*Sensitive data* - user will be prompted on standard input) The password to unlock your keystore. Due to a limitation in Tomcat, this must match your key's password in the keystore.
 * `--keystore-type`: The type of your keystore. Examples include JKS, and PKCS12.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--truststore`: Path to the truststore holding your trusted certificates.
 * `--truststore-password`: (*Sensitive data* - user will be prompted on standard input) The password to unlock your truststore.
 * `--truststore-type`: The type of your truststore. Examples include JKS, and PKCS12.


---
## hal config security api ssl enable

Enable SSL for the API gateway.

#### Usage
```
hal config security api ssl enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security authn

This set of commands allows you to configure how users can authenticate against Spinnaker.

#### Usage
```
hal config security authn [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `oauth2`: Configure the oauth2 method for authenticating.
 * `saml`: Configure the saml method for authenticating.

---
## hal config security authn oauth2

Configure the oauth2 method for authenticating.

#### Usage
```
hal config security authn oauth2 [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Set the oauth2 method as disabled
 * `edit`: Edit the oauth2 authentication method.
 * `enable`: Set the oauth2 method as enabled

---
## hal config security authn oauth2 disable

Set the oauth2 method as disabled

#### Usage
```
hal config security authn oauth2 disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security authn oauth2 edit

Edit the oauth2 authentication method.

#### Usage
```
hal config security authn oauth2 edit [parameters]
```

#### Parameters
 * `--client-id`: The OAuth client ID you have configured with your OAuth provider.
 * `--client-secret`: The OAuth client secret you have configured with your OAuth provider.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--pre-established-redirect-uri`: The externally accessible URL for Gate. For use with load balancers that do any kind of address manipulation for Gate traffic, such as an SSL terminating load balancer.
 * `--provider`: The OAuth provider handling authentication. The supported options are Google, GitHub, and Azure
 * `--user-info-requirements`: (*Default*: `(empty)`) The map of requirements the userInfo request must have. This is used to restrict user login to specific domains or having a specific attribute. Use equal signs between key and value, and additional key/value pairs need to repeat the flag. Example: '--user-info-requirements foo=bar --userInfoRequirements baz=qux'.


---
## hal config security authn oauth2 enable

Set the oauth2 method as enabled

#### Usage
```
hal config security authn oauth2 enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security authn saml

Configure the saml method for authenticating.

#### Usage
```
hal config security authn saml [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Set the saml method as disabled
 * `edit`: Configure authentication using a SAML identity provider.
 * `enable`: Set the saml method as enabled

---
## hal config security authn saml disable

Set the saml method as disabled

#### Usage
```
hal config security authn saml disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security authn saml edit

SAML authenticates users by passing cryptographically signed XML documents between the Gate server and an identity provider. Gate's key is stored and accessed via the --keystore  parameters, while the identity provider's keys are included in the metadata.xml. Finally, the identity provider must redirect the control flow (through the user's browser) back to Gate by way of the --serviceAddressUrl. This is likely the address of Gate's load balancer.

#### Usage
```
hal config security authn saml edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--issuer-id`: The identity of the Spinnaker application registered with the SAML provider.
 * `--keystore`: Path to the keystore that contains this server's private key. This key is used to cryptographically sign SAML AuthNRequest objects.
 * `--keystore-alias`: The name of the alias under which this server's private key is stored in the --keystore file.
 * `--keystore-password`: The password used to access the file specified in --keystore
 * `--metadata`: The address to your identity provider's metadata XML file. This can be a URL or the path of a local file.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--service-address-url`: The address of the Gate server that will be accesible by the SAML identity provider. This should be the full URL, including port, e.g. https://gate.org.com:8084/. If deployed behind a load balancer, this would be the laod balancer's address.


---
## hal config security authn saml enable

Set the saml method as enabled

#### Usage
```
hal config security authn saml enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security authz

This set of commands allows you to configure what resources users of Spinnaker can read and modify.

#### Usage
```
hal config security authz [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Set Spinnaker's role-based authorization to disabled
 * `edit`: Edit your roles provider settings.
 * `enable`: Set Spinnaker's role-based authorization to enabled
 * `github`: Configure the github role provider.
 * `google`: Configure the google role provider.

---
## hal config security authz disable

Set Spinnaker's role-based authorization to disabled

#### Usage
```
hal config security authz disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security authz edit

Edit your roles provider settings.

#### Usage
```
hal config security authz edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--type`: Set a roles provider type


---
## hal config security authz enable

Set Spinnaker's role-based authorization to enabled

#### Usage
```
hal config security authz enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security authz github

Configure the github role provider.

#### Usage
```
hal config security authz github [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Edit the github role provider.

---
## hal config security authz github edit

Edit the github role provider.

#### Usage
```
hal config security authz github edit [parameters]
```

#### Parameters
 * `--accessToken`: A personal access token of an account with access to your organization's GitHub Teams structure.
 * `--baseUrl`: Used if using GitHub enterprise some other non github.com GitHub installation.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--organization`: The GitHub organization under which to query for GitHub Teams.


---
## hal config security authz google

Configure the google role provider.

#### Usage
```
hal config security authz google [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Edit the google role provider.

---
## hal config security authz google edit

Edit the google role provider.

#### Usage
```
hal config security authz google edit [parameters]
```

#### Parameters
 * `--admin-username`: Your role provider's admin username e.g. admin@myorg.net
 * `--credential-path`: A path to a valid json service account that can authenticate against the Google role provider.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--domain`: The domain your role provider is configured for e.g. myorg.net.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security ui

Configure and view the UI server's addressable URL.

#### Usage
```
hal config security ui [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Configure access policies specific to Spinnaker's UI server.
 * `ssl`: Configure and view SSL settings for Spinnaker's UI gateway.

---
## hal config security ui edit

When Spinnaker is deployed to a remote host, the UI server may be configured to do SSL termination, or sit behind an externally configured proxy server or load balancer.

#### Usage
```
hal config security ui edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--override-base-url`: If you are accessing the UI server remotely, provide the full base URL of whatever proxy or load balancer is fronting the UI requests.


---
## hal config security ui ssl

If you want the UI server to do SSL termination, it must be enabled and configured here. If you are doing your own SSL termination, leave this disabled.

#### Usage
```
hal config security ui ssl [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `disable`: Disable SSL for the UI gateway.
 * `edit`: Edit SSL settings for your UI server.
 * `enable`: Enable SSL for the UI gateway.

---
## hal config security ui ssl disable

Disable SSL for the UI gateway.

#### Usage
```
hal config security ui ssl disable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config security ui ssl edit

Configure SSL termination to handled by the UI server's Apache server.

#### Usage
```
hal config security ui ssl edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--ssl-certificate-file`: Path to your .crt file.
 * `--ssl-certificate-key-file`: Path to your .key file.
 * `--ssl-certificate-passphrase`: (*Sensitive data* - user will be prompted on standard input) The passphrase needed to unlock your SSL certificate. This will be provided to Apache on startup.


---
## hal config security ui ssl enable

Enable SSL for the UI gateway.

#### Usage
```
hal config security ui ssl enable [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal config storage

Show Spinnaker's persistent storage configuration.

#### Usage
```
hal config storage [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `azs`: Manage and view Spinnaker configuration for the "azs" persistent store.
 * `edit`: Edit Spinnaker's persistent storage.
 * `gcs`: Manage and view Spinnaker configuration for the "gcs" persistent store.
 * `oraclebmcs`: Manage and view Spinnaker configuration for the "oraclebmcs" persistent store.
 * `s3`: Manage and view Spinnaker configuration for the "s3" persistent store.

---
## hal config storage azs

Manage and view Spinnaker configuration for the "azs" persistent store.

#### Usage
```
hal config storage azs [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Edit configuration for the "azs" persistent store.

---
## hal config storage azs edit

Edit configuration for the "azs" persistent store.

#### Usage
```
hal config storage azs edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--storage-account-key`: The key to access the Azure Storage Account used for Spinnaker's persistent data.
 * `--storage-account-name`: The name of an Azure Storage Account used for Spinnaker's persistent data.
 * `--storage-container-name`: (*Default*: `spinnaker`) The container name in the chosen storage account to place all of Spinnaker's persistent data.


---
## hal config storage edit

Edit Spinnaker's persistent storage.

#### Usage
```
hal config storage edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--type`: (*Required*) The type of the persistent store to use for Spinnaker.


---
## hal config storage gcs

Manage and view Spinnaker configuration for the "gcs" persistent store.

#### Usage
```
hal config storage gcs [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Edit configuration for the "gcs" persistent store.

---
## hal config storage gcs edit

Edit configuration for the "gcs" persistent store.

#### Usage
```
hal config storage gcs edit [parameters]
```

#### Parameters
 * `--bucket`: The name of a storage bucket that your specified account has access to. If not specified, a random name will be chosen. If you specify a globally unique bucket name that doesn't exist yet, Halyard will create that bucket for you.
 * `--bucket-location`: This is only required if the bucket you specify doesn't exist yet. In that case, the bucket will be created in that location. See https://cloud.google.com/storage/docs/managing-buckets#manage-class-location.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--json-path`: A path to a JSON service account with permission to read and write to the bucket to be used as a backing store.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--project`: The Google Cloud Platform project you are using to host the GCS bucket as a backing store.
 * `--root-folder`: The root folder in the chosen bucket to place all of Spinnaker's persistent data in.


---
## hal config storage oraclebmcs

Manage and view Spinnaker configuration for the "oraclebmcs" persistent store.

#### Usage
```
hal config storage oraclebmcs [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Edit configuration for the "oraclebmcs" persistent store.

---
## hal config storage oraclebmcs edit

Edit configuration for the "oraclebmcs" persistent store.

#### Usage
```
hal config storage oraclebmcs edit [parameters]
```

#### Parameters
 * `--bucket-name`: The bucket name to store persistent state object in
 * `--compartment-id`: Provide the OCID of the Oracle BMCS Compartment to use.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--fingerprint`: Fingerprint of the public key
 * `--namespace`: The namespace the bucket and objects should be created in
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--region`: An Oracle BMCS region (e.g., us-phoenix-1)
 * `--ssh-private-key-file-path`: Path to the private key in PEM format
 * `--tenancy-id`: Provide the OCID of the Oracle BMCS Tenancy to use.
 * `--user-id`: Provide the OCID of the Oracle BMCS User you're authenticating as


---
## hal config storage s3

Manage and view Spinnaker configuration for the "s3" persistent store.

#### Usage
```
hal config storage s3 [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Edit configuration for the "s3" persistent store.

---
## hal config storage s3 edit

Edit configuration for the "s3" persistent store.

#### Usage
```
hal config storage s3 edit [parameters]
```

#### Parameters
 * `--access-key-id`: Your AWS Access Key ID. If not provided, Halyard/Spinnaker will try to find AWS credentials as described at http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default
 * `--assume-role`: If set, Halyard will configure a credentials provider that uses AWS Security Token Service to assume the specified role.

Example: "user/spinnaker" or "role/spinnakerManaged"
 * `--bucket`: The name of a storage bucket that your specified account has access to. If not specified, a random name will be chosen. If you specify a globally unique bucket name that doesn't exist yet, Halyard will create that bucket for you.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--endpoint`: An alternate endpoint that your S3-compatible storage can be found at. This is intended for self-hosted storage services with S3-compatible APIs, e.g. Minio. If supplied, this storage type cannot be validated.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--region`: This is only required if the bucket you specify doesn't exist yet. In that case, the bucket will be created in that region. See http://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region.
 * `--root-folder`: The root folder in the chosen bucket to place all of Spinnaker's persistent data in.
 * `--secret-access-key`: (*Sensitive data* - user will be prompted on standard input) Your AWS Secret Key.


---
## hal config version

Configure & view the current deployment of Spinnaker's version.

#### Usage
```
hal config version [parameters] [subcommands]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.

#### Subcommands
 * `edit`: Set the desired Spinnaker version.

---
## hal config version edit

Set the desired Spinnaker version.

#### Usage
```
hal config version edit [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--version`: (*Required*) Must be either a version number "X.Y.Z" for a specific release of Spinnaker, or "$BRANCH-latest-unvalidated" for the most recently built (unvalidated) Spinnaker on $BRANCH.


---
## hal deploy

Manage the deployment of Spinnaker. This includes where it's deployed, what the infrastructure footprint looks like, what the currently running deployment looks like, etc...

#### Usage
```
hal deploy [subcommands]
```

#### Subcommands
 * `apply`: Deploy or update the currently configured instance of Spinnaker to a selected environment.
 * `clean`: Remove all Spinnaker artifacts in your target deployment environment.
 * `collect-logs`: Collect logs from the specified Spinnaker services.
 * `connect`: Connect to your Spinnaker deployment.
 * `details`: Get details about your currently deployed Spinnaker installation.
 * `diff`: This shows what changes you have made since Spinnaker was last deployed.
 * `rollback`: Rollback Spinnaker to the prior version on a selected environment.

---
## hal deploy apply

This command deploys Spinnaker, depending on how you've configured your deployment. Local deployments are applied to the machine running Halyard, whereas Distributed deployments are applied to a cloud provider. Local deployments are subject to downtime during updates, whereas Distributed deployments are deployed and updated via a headless 'bootstrap' deployment of Spinnaker, and don't suffer downtime.

#### Usage
```
hal deploy apply [parameters]
```

#### Parameters
 * `--auto-run`: This command will generate a script to be run on your behalf. By default, the script will run without intervention - if you want to override this, provide "true" or "false" to this flag.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--omit-config`: (*Default*: `false`) WARNING: This is considered an advanced command, and may break your deployment if used incorrectly.

 This guarantees that no configuration will be generated for this deployment. This is useful for staging artifacts for later manual configuration.
 * `--service-names`: (*Default*: `[]`) When supplied, only install or update the specified Spinnaker services.


---
## hal deploy clean

This command destroys all Spinnaker artifacts in your target deployment environment. This cannot be undone, so use with care.

#### Usage
```
hal deploy clean [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal deploy collect-logs

This command collects logs from all Spinnaker services, and depending on how it was deployed, it will collect logs from sidecars and startup scripts as well.

#### Usage
```
hal deploy collect-logs [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--service-names`: (*Default*: `[]`) When supplied, logs from only the specified services will be collected.


---
## hal deploy connect

This command connects to your Spinnaker deployment, assuming it was already deployed. In the case of the `Local*` deployment type, this is a NoOp.

#### Usage
```
hal deploy connect [parameters]
```

#### Parameters
 * `--auto-run`: This command will generate a script to be run on your behalf. By default, the script will run without intervention - if you want to override this, provide "true" or "false" to this flag.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--service-names`: (*Default*: `[]`) When supplied, connections to the specified Spinnaker services are opened. When omitted, connections to the UI & API servers are opened to allow you to interact with Spinnaker in your browser.


---
## hal deploy details

Get details about your currently deployed Spinnaker installation.

#### Usage
```
hal deploy details [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--service-name`: (*Required*) The name of the service to inspect.


---
## hal deploy diff

This shows what changes you have made since Spinnaker was last deployed.

#### Usage
```
hal deploy diff [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal deploy rollback

This command attempts to rollback Spinnaker to the prior deployed version, depending on how you've configured your deployment. Local deployments have their prior packages installed and reconfigured, whereas Distributed deployments are rolled back via a headless 'bootstrap' deployment of Spinnaker, and don't suffer downtime.

#### Usage
```
hal deploy rollback [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.
 * `--service-names`: (*Default*: `[]`) When supplied, only install or update the specified Spinnaker services.


---
## hal task

Every unit of work Halyard carries out is bundled in a Task. This set  of commands exposes some information about these tasks. The commands here are mainly for troubleshooting.

#### Usage
```
hal task [subcommands]
```

#### Subcommands
 * `interrupt`: Interrupt (attempt to kill) a given task.
 * `list`: List the currently running Tasks.

---
## hal task interrupt

Interrupt (attempt to kill) a given task.

#### Usage
```
hal task interrupt UUID
```


---
## hal task list

List the currently running Tasks.

#### Usage
```
hal task list
```


---
## hal version

Get information about the available Spinnaker versions.

#### Usage
```
hal version [subcommands]
```

#### Subcommands
 * `bom`: Get the Bill of Materials (BOM) for the specified version.
 * `latest`: Get the latest released, validated version number of Spinnaker.
 * `list`: List the available Spinnaker versions and their changelogs.

---
## hal version bom

The Bill of Materials (BOM) is the manifest Halyard and Spinnaker use to agree on what subcomponent versions comprise a top-level release of Spinnaker. This command can be used with a main parameter (VERSION) to get the BOM for a given version of Spinnaker, or without a parameter to get the BOM for whatever version of Spinnaker you are currently configuring.

#### Usage
```
hal version bom VERSION [parameters]
```

#### Parameters
`VERSION`: The version whose Bill of Materials (BOM) to lookup.
 * `--artifact-name`: When supplied, print the version of this artifact only.
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---
## hal version latest

Get the latest released, validated version number of Spinnaker.

#### Usage
```
hal version latest
```


---
## hal version list

All Spinnaker releases that have been fully validated are listed here. You can pick one of these releases to deploy using the `hal config version edit` command. There are unlisted, non-supported releases as well, but we advise against running them. For more information, contact the developers at http://join.spinnaker.io.

#### Usage
```
hal version list [parameters]
```

#### Parameters
 * `--deployment`: If supplied, use this Halyard deployment. This will _not_ create a new deployment.
 * `--no-validate`: (*Default*: `false`) Skip validation.


---

