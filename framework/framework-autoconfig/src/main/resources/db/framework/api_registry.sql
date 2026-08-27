CREATE TABLE IF NOT EXISTS api_registry (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  module_code TEXT NOT NULL,
  module_name TEXT NOT NULL DEFAULT '',
  module_base_path TEXT NOT NULL DEFAULT '',
  module_desc TEXT NOT NULL DEFAULT '',
  controller_desc TEXT NOT NULL DEFAULT '',
  method TEXT NOT NULL,
  path TEXT NOT NULL,
  api_name TEXT NOT NULL DEFAULT '',
  summary TEXT NOT NULL DEFAULT '',
  description TEXT NOT NULL DEFAULT '',
  tags TEXT DEFAULT NULL,
  request_params TEXT DEFAULT NULL,
  response_example TEXT DEFAULT NULL,
  auth_required INTEGER NOT NULL DEFAULT 1,
  deprecated INTEGER NOT NULL DEFAULT 0,
  hash TEXT NOT NULL DEFAULT '',
  version TEXT NOT NULL DEFAULT '1.0.0',
  status INTEGER NOT NULL DEFAULT 1,
  sort INTEGER NOT NULL DEFAULT 0,
  is_deleted INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_api_registry_hash ON api_registry (hash);
