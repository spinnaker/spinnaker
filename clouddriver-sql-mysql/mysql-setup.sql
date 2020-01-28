DROP DATABASE IF EXISTS clouddriver;
SET tx_isolation = 'READ-COMMITTED';

CREATE DATABASE clouddriver;
CREATE USER clouddriver_migrate;
CREATE USER clouddriver_service;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER, LOCK TABLES, EXECUTE, SHOW VIEW ON `clouddriver`.* TO 'clouddriver_migrate'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, EXECUTE, SHOW VIEW ON `clouddriver`.* TO 'clouddriver_service'@'%';
