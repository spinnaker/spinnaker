import os
import yaml
from google.cloud import storage
from google.oauth2 import service_account
from .storage import Storage

class GcsStorage(Storage):
    def __init__(self, bucket, path, project=None, json_path=None):
        if bucket is None:
            raise ValueError('Bucket must be supplied to GCS storage')
        if path is None:
            path = 'spinbot/cache'

        self.path = path
        if json_path is not None:
            json_path = os.path.expanduser(json_path)
            credentials = service_account.Credentials.from_service_account_file(json_path)
            if credentials.requires_scopes:
                credentials = credentials.with_scopes(['https://www.googleapis.com/auth/devstorage.read_write'])
            self.client = storage.Client(project=project, credentials=credentials)
        else:
            self.client = storage.Client()

        if self.client.lookup_bucket(bucket) is None:
            self.client.create_bucket(bucket)

        self.bucket = self.client.get_bucket(bucket)

        super().__init__()

    def store(self, key, val):
        b = self.bucket.get_blob(self.path)
        contents = '{}'
        if b:
            contents = b.download_as_string()
        else:
            b = self.bucket.blob(self.path)

        props = yaml.safe_load(contents)
        if props is None:
            props = {}

        props[key] = val
        b.upload_from_string(yaml.safe_dump(props))

    def load(self, key):
        b = self.bucket.get_blob(self.path)
        contents = '{}'
        if b:
            contents = b.download_as_string()
        else:
            b = self.bucket.blob(self.path)

        props = yaml.safe_load(contents)
        if props is None:
            props = {}

        return props.get(key)
