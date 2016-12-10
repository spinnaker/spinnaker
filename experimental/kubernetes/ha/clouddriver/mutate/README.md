# Mutating Clouddriver

This set of clouddriver instances exists to perform mutating calls on the
registered cloud providers. It will receive requests exclusively from orca, and
will need to write to the cache only for single item updates.
