CREATE KEYSPACE IF NOT EXISTS echo
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };
