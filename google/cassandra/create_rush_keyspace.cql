CREATE KEYSPACE IF NOT EXISTS rush
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };
