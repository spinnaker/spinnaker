CREATE KEYSPACE IF NOT EXISTS front50
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };
