from subprocess import check_output, STDOUT, CalledProcessError

class NoGCloudError(Exception):
    def __init__(self, *args, **kwargs):
        Exception.__init__(self, *args, **kwargs)

class GCloud(object):
    @staticmethod
    def _wrap(func):
        try:
            return func()
        except OSError as oserr:
            if "No such file or directory" in oserr.output:
                raise NoGCloudError()

    @staticmethod
    def project():
        res = GCloud._wrap(lambda:
                check_output([
                    "gcloud",
                    "info",
                    "--format=value(config.project)"
                    ]))

        return res.strip()

    @staticmethod
    def create_service_account(project, name):
        try:
            GCloud._wrap(lambda:
                    check_output([
                        "gcloud",
                        "iam",
                        "service-accounts",
                        "create", name,
                        "--display-name", name
                        ], stderr=STDOUT))
        except CalledProcessError as e:
            if "ALREADY_EXISTS" not in e.output:
                raise e

        email = GCloud._wrap(lambda:
                check_output([
                    "gcloud",
                    "iam",
                    "service-accounts",
                    "list",
                    "--filter", "displayName:{}".format(name),
                    "--format", "value(email)"
                    ]))
        return email.strip()


    @staticmethod
    def grant_role(project, role, email):
        return GCloud._wrap(lambda:
                check_output([
                    "gcloud",
                    "projects",
                    "add-iam-policy-binding", project,
                    "--role", role,
                    "--member", "serviceAccount:{}".format(email)
                    ]))

    @staticmethod
    def download_key(path, email):
        return GCloud._wrap(lambda:
                check_output([
                    "gcloud",
                    "iam",
                    "service-accounts",
                    "keys",
                    "create", path,
                    "--iam-account", email
                    ]))
