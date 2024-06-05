/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE USER 'clouddriver_service'@'%' IDENTIFIED BY 'c10uddriver';
CREATE USER 'clouddriver_migrate'@'%' IDENTIFIED BY 'c10uddriver';

GRANT
  SELECT, INSERT, UPDATE, DELETE, CREATE, EXECUTE, SHOW VIEW
    ON *.*
  TO 'clouddriver_service'@'%';

GRANT
  SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER, LOCK TABLES, EXECUTE, SHOW VIEW
    ON *.*
  TO 'clouddriver_migrate'@'%';
