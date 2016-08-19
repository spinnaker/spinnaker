import yaml
from gcloud import GCloud, NoGCloudError
from functools import partial
from subprocess import Popen, STDOUT, check_output, CalledProcessError
from os import path
from interface import Console

class Clouddriver(object):
    def __init__(self, account_file):
        self.account_file = account_file
        self.options = ["kubernetes", "dockerRegistry", "save"]

    def save(self):
        Console.highlight("Writing configuration to {}...".format(
            self.account_file))
        with open(self.account_file, "w") as f:
            f.write(yaml.dump(self.config, default_flow_style=False))
        Console.show(yaml.dump(self.config, default_flow_style=False))
        Console.highlight("Done.")

    def configure(self):
        try:
            with open(self.account_file, "r") as stream:
                self.config = yaml.load(stream)
                Console.highlight("This is the current config:")
                Console.show(yaml.dump(self.config, default_flow_style=False))
        except IOError:
            Console.highlight("There is no written account config yet.")
        except yaml.YAMLError as yerr:
            raise ValueError(("Provided account file {} is not valid yaml: "
                    + "{}").format(self.account_file, yerr))

        Console.configure(self,
                prompt="Pick a provider, or save your configuration.")

    def dockerRegistry(self):
        self.configure_provider("dockerRegistry")

    def kubernetes(self):
        self.configure_provider("kubernetes")

    def configure_provider(self, provider):
        provider_config = self.config.get(provider, None)

        if provider_config is None:
            Console.highlight("This provider has not yet been configured.")
        else:
            Console.highlight("The current configuration for the selected "
                    + "provider is:")
            Console.show(yaml.dump(provider_config, default_flow_style=False))

        try:
            Console.configure(ClouddriverProviderFactory(self.config, 
                        self.account_file).with_provider(provider),
                    prompt="Pick an action to perform on provider {}".format(
                        provider))
        except ValueError as err:
            Console.warn("Critical: {}".format(err.output))

class ClouddriverProvider(Clouddriver):
    def __init__(self, config, provider_config, account_file):
        Clouddriver.__init__(self, account_file)
        self.config = config
        self.provider_config = provider_config
        self.accounts = provider_config.get("accounts", [])
        self.options = ["add_account", "enable_provider", "disable_provider",
                "show_config", "edit_account", "delete_account", "save"]
        if self.accounts == []:
            provider_config["accounts"] = self.accounts

    def _get_account_by_name(self, account_name):
        indexes = [(i, v) for i, v in enumerate(self.accounts)
                if v["name"] == account_name]
        if indexes and len(indexes) == 1:
            return indexes[0]
        # Raise exceptions since this should be an unreachable state.
        elif not indexes:
            raise ValueError(("Unconfigured account {} cannot be "
                    + "selected").format(account_name))
        elif len(indexes) > 1:
            raise ValueError(("Ambiguous account {} appears more "
                    + "than once").format(account_name))

    def _delete_account(self, account_name):
        self.accounts.pop(self._get_account_by_name(account_name)[0])
        self.show_config()

    def delete_account(self):
        Console.configure(self,
                options=self._get_account_names(),
                op_func="_delete_account",
                prompt="Pick an account to delete.",
                once=True)

    def edit_account(self):
        Console.configure(self,
                options=self._get_account_names(),
                op_func="_edit_account",
                prompt="Pick an account to edit.",
                once=True)

    def _edit_account(self, account_name):
        account = self._get_account_by_name(account_name)[1]

        account["name"] = Console.prompt("Edit the account name: ",
                default=account_name, required=True)

        self._write_account(account)

        Console.highlight("Edited account: ")
        Console.show(yaml.dump(account, default_flow_style=False))

    def add_account(self):
        account = {}
        account["name"] = Console.prompt("Provide an account name: ",
                required=True)

        self._write_account(account)
        self.accounts.append(account)

        Console.highlight("Newly configured account: ")
        Console.show(yaml.dump(account, default_flow_style=False))

    def _get_account_names(self):
        return [account["name"] for account in self.accounts]

    def disable_provider(self):
        self.provider_config["enabled"] = False
        self.show_config()

    def enable_provider(self):
        self.provider_config["enabled"] = True
        self.show_config()

    def show_config(self):
        Console.highlight("Your current configuration is:")
        Console.show(yaml.dump(self.provider_config,
                default_flow_style=False))

class KubernetesProvider(ClouddriverProvider):
    def __init__(self, config, account_file):
        ClouddriverProvider.__init__(self,
                config,
                config.get("kubernetes", None),
                account_file)
        self.docker_accounts = (DockerRegistryProvider(config, account_file)
                ._get_account_names())
        Console.show("Searching for kubeconfig file in ~/.kube/config...")
        try:
            with open(path.join(path.expanduser("~"), ".kube", "config"),
                    "r") as stream:
                self.kubeconfig = yaml.load(stream)
            Console.show("kubeconfig found.")
        except IOError:
            raise ValueError("You need a running kubernetes cluster with "
                    + "credentials stored in your ~/.kube/config file.")
        except yaml.YAMLError as yerr:
            raise ValueError(("Provided kubeconfig file {} is not valid yaml: "
                    + "{}").format(self.account_file, yerr))

    def _write_account(self, account):
        self.account = account
        context = account.get("context", None)
        if context is None:
            context = self.kubeconfig.get("current-context", None)

        contexts = self.kubeconfig.get("contexts", [])
        contexts_by_name = []
        try:
            contexts_by_name = ["{}".format(elem["name"])
                    for elem in contexts]
        except KeyError as k:
            raise ValueError("Malformed kubeconfig: {}".format(k.output))

        context = Console.multiselect(contexts_by_name,
                prompt="Pick a Kubernetes context to manage: ",
                default=context)

        account["context"] = context
        
        invalid = True
        while invalid:
            registries = Console.prompt("Provide a list of space-separated " 
                    + "docker registry accounts: ", 
                    required=True, 
                    options=self.docker_accounts,
                    default=self.docker_accounts, 
                    split=True)

            invalid = False
            for registry in registries:
                if registry not in self.docker_accounts:
                    invalid = True
                    Console.warn(("{} is not a valid registry, must be found "
                            + "in {}").format(registry, self.docker_accounts))

        account["dockerRegistries"] = [{ "accountName": registry } 
                for registry in registries]


class ListController(object):
    def __init__(self, current, options):
        self._set_current(current)
        self._set_options(options)

    def _set_current(self, current):
        self.current = current
        self.current_set = set(current)

    def _set_options(self, options):
        self.options = options
        self.options_set = set(options)

    def add_all(self):
        self._set_current(self.options)

    def add_one(self, option):
        self._set_current(list(self.current.union([option])))
        self._set_options(list(self.current.difference([option])))

    def remove_one(self, option):
        self._set_current(list(self.current.difference([option])))
        self._set_options(list(self.current.union([option])))
    
    def remove_all(self):
        self._set_current([])
        self._set_options(list(self.options.union(self.current)))

class DockerRegistryProvider(ClouddriverProvider):
    def __init__(self, config, account_file):
        ClouddriverProvider.__init__(self,
                config,
                config.get("dockerRegistry", None),
                account_file)

    def _write_account(self, account):
        account["address"] = Console.multiselect(["gcr.io", "us.gcr.io",
                "eu.gcr.io", "asia.gcr.io", "b.gcr.io", "index.docker.io",
                "quay.io"],
                prompt="Provide the registry address name: ",
                default=account.get('address', None), custom=True)

        if "gcr.io" in account["address"]:
            Console.info("You are hosting these images on GCR - this requires\n"
                    + "a GCP service account with permission to read from\n"
                    + "storage in the same project as your registry.\n"
                    + "This next step requires 'gcloud' to be installed.\n\n"
                    + "The service account will be stored in this location:")

            sa_path = path.join(path.expanduser("~"), ".spin", "gcr",
                    "{}.json".format(account["name"]))
            account["passwordFile"] = path.join("/root", ".gcp",
                    "{}.json".format(account["name"]))
            account["username"] = "_json_key"

            Console.underline(sa_path)

            write = True
            if path.isfile(sa_path):
                write = Console.multiselect(["yes", "no"],
                        prompt="The service account already exists, overwrite?")
                write = write == "yes"

            if write:
                try:
                    project = GCloud.project()

                    project = Console.prompt("Provide the registry project: ",
                            required=True, default=project)

                    email = GCloud.create_service_account(project,
                            account["name"])
                    GCloud.grant_role(project,
                            "roles/compute.storageAdmin",
                            email)
                    GCloud.download_key(sa_path, email)
                    account["email"] = email
                except NoGCloudError:
                    Console.warn("'gcloud' is not installed. Please visit")
                    Console.underline("https://cloud.google.com/sdk/")
                    return
        else:
            account["username"] = Console.prompt("Registry username "
                    + "(optional) ")
            account["password"] = Console.prompt_pass("Registry password "
                    + "(optional) ")

        repos = Console.prompt("Enter list of space-separated repositories "
                + "to index (none for all): ", split=True)

        account["repositories"] = repos

class ClouddriverProviderFactory(object):
    def __init__(self, config, account_file):
        self.config = config
        self.account_file = account_file

    def with_provider(self, provider):
        if provider == "dockerRegistry":
            return DockerRegistryProvider(self.config, self.account_file)
        if provider == "kubernetes":
            return KubernetesProvider(self.config, self.account_file)
        else:
            raise ValueError("{} is not yet supported".format(provider))
