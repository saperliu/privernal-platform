-- SQLite 平台库建表脚本（由 tools/sqlite-migration 从运行库元数据生成，与 release/sql/init.sql 对应）
-- 含表结构、索引与最小种子数据（种子段附于文末，表/菜单/账号口径与 sql/init.sql 一致）。

CREATE TABLE IF NOT EXISTS dynamic_dashboard (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  dashboard_code TEXT NOT NULL,
  dashboard_name TEXT,
  app_id TEXT,
  config TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_dashboard_code UNIQUE (dashboard_code)
);
CREATE INDEX IF NOT EXISTS idx_dashboard_app ON dynamic_dashboard (app_id);

CREATE TABLE IF NOT EXISTS dynamic_dashboard_item (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  dashboard_code TEXT NOT NULL,
  report_code TEXT NOT NULL,
  title TEXT,
  span INTEGER DEFAULT 6,
  order_num INTEGER DEFAULT 0,
  config TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ddi_dashboard ON dynamic_dashboard_item (dashboard_code);
CREATE INDEX IF NOT EXISTS idx_ddi_report ON dynamic_dashboard_item (report_code);

CREATE TABLE IF NOT EXISTS dynamic_database (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER,
  database_name TEXT,
  database_type TEXT,
  driver TEXT,
  password TEXT,
  url TEXT,
  user_name TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS dynamic_dataset (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  dataset_code TEXT NOT NULL,
  dataset_name TEXT,
  table_code TEXT,
  app_id TEXT,
  column_snapshot TEXT,
  default_filter TEXT,
  source_type TEXT DEFAULT 'dynamic_table',
  source_config TEXT,
  refresh_config TEXT,
  computed_columns TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_dataset_code UNIQUE (dataset_code)
);
CREATE INDEX IF NOT EXISTS idx_dataset_app ON dynamic_dataset (app_id);

CREATE TABLE IF NOT EXISTS dynamic_datav_asset (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  asset_code TEXT NOT NULL,
  asset_name TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT '通用',
  asset_type TEXT NOT NULL DEFAULT 'image',
  file_url TEXT NOT NULL,
  original_name TEXT,
  file_size INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_datav_asset_code UNIQUE (asset_code)
);
CREATE INDEX IF NOT EXISTS idx_datav_asset_category ON dynamic_datav_asset (category);

CREATE TABLE IF NOT EXISTS dynamic_datav_file (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  file_code TEXT NOT NULL,
  dataset_code TEXT,
  file_name TEXT NOT NULL,
  size_bytes INTEGER NOT NULL DEFAULT 0,
  content BLOB,
  PRIMARY KEY (id),
  CONSTRAINT uk_datav_file_code UNIQUE (file_code)
);
CREATE INDEX IF NOT EXISTS idx_datav_file_dataset ON dynamic_datav_file (dataset_code);

CREATE TABLE IF NOT EXISTS dynamic_datav_screen (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  screen_code TEXT NOT NULL,
  screen_name TEXT NOT NULL,
  app_id TEXT,
  layout_mode TEXT DEFAULT 'absolute',
  schema_json TEXT,
  status INTEGER DEFAULT 0,
  thumbnail TEXT,
  widget_count INTEGER DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_datav_screen_code UNIQUE (screen_code)
);
CREATE INDEX IF NOT EXISTS idx_datav_screen_app ON dynamic_datav_screen (app_id);

CREATE TABLE IF NOT EXISTS dynamic_datav_share (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  share_code TEXT NOT NULL,
  screen_code TEXT NOT NULL,
  expire_time TEXT,
  referer_whitelist TEXT,
  enabled TEXT NOT NULL DEFAULT 'Y',
  PRIMARY KEY (id),
  CONSTRAINT uk_datav_share_code UNIQUE (share_code)
);
CREATE INDEX IF NOT EXISTS idx_datav_share_screen ON dynamic_datav_share (screen_code);

CREATE TABLE IF NOT EXISTS dynamic_datav_version (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  remark TEXT,
  screen_code TEXT NOT NULL,
  version_no INTEGER NOT NULL,
  screen_name TEXT,
  schema_json TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_datav_version UNIQUE (screen_code, version_no)
);


CREATE TABLE IF NOT EXISTS dynamic_doc_template (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT NOT NULL DEFAULT '1970-01-01 00:00:00',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  template_code TEXT NOT NULL,
  name TEXT NOT NULL,
  app_id TEXT,
  form_id TEXT,
  source_type TEXT DEFAULT 'FORM',
  source_code TEXT,
  template_type TEXT DEFAULT 'DOC',
  status TEXT NOT NULL DEFAULT '1',
  content_html TEXT,
  content_sheet TEXT,
  binding_json TEXT,
  params_json TEXT,
  print_config TEXT,
  is_default TEXT NOT NULL DEFAULT 'N',
  sort INTEGER DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT UNI_template_code UNIQUE (template_code, deleted_time)
);
CREATE INDEX IF NOT EXISTS idx_form ON dynamic_doc_template (form_id);

CREATE TABLE IF NOT EXISTS dynamic_form (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER,
  table_code TEXT NOT NULL,
  table_name TEXT,
  meta_data TEXT,
  is_workflow INTEGER,
  workflow_code TEXT,
  app_id TEXT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT code_unque UNIQUE (table_code)
);


CREATE TABLE IF NOT EXISTS dynamic_form_column (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER,
  form_id TEXT,
  filed_code TEXT,
  filed_name TEXT,
  filed_type TEXT,
  length INTEGER,
  scale INTEGER,
  sort INTEGER,
  default_value TEXT,
  query_default_value TEXT,
  order_dir TEXT,
  show_list INTEGER,
  show_query INTEGER,
  sensitive_flag TEXT DEFAULT 'N',
  unique_flag TEXT DEFAULT 'N',
  component_id TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS dynamic_form_io_config (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  form_id TEXT NOT NULL,
  export_enabled TEXT NOT NULL DEFAULT 'N',
  import_enabled TEXT NOT NULL DEFAULT 'N',
  export_scope TEXT DEFAULT 'SELF',
  export_columns TEXT,
  sensitive_policy TEXT DEFAULT 'MASK',
  dup_strategy TEXT DEFAULT 'SKIP',
  strict_validate TEXT DEFAULT 'Y',
  import_limit INTEGER DEFAULT 5000,
  import_async TEXT DEFAULT 'Y',
  run_backscript TEXT DEFAULT 'N',
  trigger_mode TEXT DEFAULT 'SUPPRESS',
  import_flow_mode TEXT DEFAULT 'NONE',
  PRIMARY KEY (id),
  CONSTRAINT uk_form UNIQUE (form_id)
);


CREATE TABLE IF NOT EXISTS dynamic_form_log (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER,
  form_id TEXT,
  version TEXT,
  meta_data TEXT,
  is_workflow INTEGER,
  workflow_code TEXT,
  log_type TEXT DEFAULT 'VERSION',
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS dynamic_form_relation (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  form_id TEXT NOT NULL,
  table_code TEXT NOT NULL,
  target_table_code TEXT NOT NULL,
  return_columns TEXT,
  has_filter INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_dfr_form ON dynamic_form_relation (form_id);
CREATE INDEX IF NOT EXISTS idx_dfr_referencer ON dynamic_form_relation (table_code, target_table_code);
CREATE INDEX IF NOT EXISTS idx_dfr_target ON dynamic_form_relation (target_table_code);

CREATE TABLE IF NOT EXISTS dynamic_form_share (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  form_id TEXT NOT NULL,
  share_type TEXT NOT NULL,
  token_digest TEXT NOT NULL,
  token_tail TEXT,
  link_cipher TEXT,
  prefill_fields TEXT,
  scope_type TEXT NOT NULL DEFAULT 'ANON_PUBLIC',
  scope_dept_ids TEXT,
  password TEXT,
  expire_time TEXT,
  submit_limit INTEGER,
  per_person_limit INTEGER DEFAULT 1,
  captcha TEXT NOT NULL DEFAULT 'N',
  editable_after_submit TEXT NOT NULL DEFAULT 'N',
  proxy_user_id TEXT,
  enabled TEXT NOT NULL DEFAULT 'Y',
  submit_count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_digest UNIQUE (token_digest)
);
CREATE INDEX IF NOT EXISTS idx_form ON dynamic_form_share (form_id, share_type);

CREATE TABLE IF NOT EXISTS dynamic_linkage_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  rule_id TEXT NOT NULL,
  form_id TEXT NOT NULL,
  rule_code TEXT,
  organization_id TEXT NOT NULL,
  database_name TEXT,
  event_type TEXT NOT NULL,
  data_id TEXT,
  event_id TEXT NOT NULL,
  fire_time TEXT,
  source_tag TEXT,
  depth INTEGER NOT NULL DEFAULT 0,
  condition_pass TEXT NOT NULL,
  condition_trace TEXT,
  status TEXT NOT NULL,
  action_results TEXT,
  error_msg TEXT,
  start_time TEXT,
  cost_ms INTEGER,
  CONSTRAINT uk_idem UNIQUE (rule_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_data ON dynamic_linkage_log (form_id, data_id);
CREATE INDEX IF NOT EXISTS idx_org_time ON dynamic_linkage_log (organization_id, start_time);
CREATE INDEX IF NOT EXISTS idx_rule ON dynamic_linkage_log (rule_id, start_time);

CREATE TABLE IF NOT EXISTS dynamic_linkage_log_archive (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  rule_id TEXT NOT NULL,
  form_id TEXT NOT NULL,
  rule_code TEXT,
  organization_id TEXT NOT NULL,
  database_name TEXT,
  event_type TEXT NOT NULL,
  data_id TEXT,
  event_id TEXT NOT NULL,
  fire_time TEXT,
  source_tag TEXT,
  depth INTEGER NOT NULL DEFAULT 0,
  condition_pass TEXT NOT NULL,
  condition_trace TEXT,
  status TEXT NOT NULL,
  action_results TEXT,
  error_msg TEXT,
  start_time TEXT,
  cost_ms INTEGER,
  CONSTRAINT uk_idem UNIQUE (rule_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_data ON dynamic_linkage_log_archive (form_id, data_id);
CREATE INDEX IF NOT EXISTS idx_org_time ON dynamic_linkage_log_archive (organization_id, start_time);
CREATE INDEX IF NOT EXISTS idx_rule ON dynamic_linkage_log_archive (rule_id, start_time);

CREATE TABLE IF NOT EXISTS dynamic_linkage_rule (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  form_id TEXT NOT NULL,
  rule_code TEXT NOT NULL,
  name TEXT NOT NULL,
  enabled TEXT NOT NULL DEFAULT 'Y',
  event_type TEXT NOT NULL,
  event_config TEXT,
  condition_tree TEXT,
  actions TEXT NOT NULL,
  async_mode TEXT NOT NULL DEFAULT 'ASYNC',
  fail_policy TEXT NOT NULL DEFAULT 'CONTINUE',
  max_depth INTEGER NOT NULL DEFAULT 1,
  target_mode TEXT DEFAULT 'PER_ROW',
  misfire_policy TEXT DEFAULT 'SKIP',
  concurrent_policy TEXT DEFAULT 'FORBID',
  timeout_seconds INTEGER DEFAULT 300,
  max_rows_per_fire INTEGER DEFAULT 5000,
  PRIMARY KEY (id),
  CONSTRAINT uk_form_code UNIQUE (form_id, rule_code)
);
CREATE INDEX IF NOT EXISTS idx_form_event ON dynamic_linkage_rule (form_id, event_type, enabled);

CREATE TABLE IF NOT EXISTS dynamic_report (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  report_code TEXT NOT NULL,
  report_name TEXT,
  report_type TEXT NOT NULL,
  dataset_code TEXT NOT NULL,
  app_id TEXT,
  config TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_report_code UNIQUE (report_code)
);
CREATE INDEX IF NOT EXISTS idx_report_app ON dynamic_report (app_id);
CREATE INDEX IF NOT EXISTS idx_report_dataset ON dynamic_report (dataset_code);

CREATE TABLE IF NOT EXISTS dynamic_script_asset (
  id TEXT NOT NULL,
  tenant_id TEXT,
  script_code TEXT NOT NULL,
  script_name TEXT NOT NULL,
  category TEXT NOT NULL,
  language TEXT NOT NULL DEFAULT 'GROOVY',
  body TEXT,
  params_schema TEXT,
  result_type TEXT NOT NULL DEFAULT 'NONE',
  timeout_seconds INTEGER NOT NULL DEFAULT 10,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  version INTEGER NOT NULL DEFAULT 1,
  app_id TEXT,
  organization_id TEXT,
  department_id TEXT,
  remark TEXT,
  revision INTEGER NOT NULL DEFAULT 0,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_script_code_org UNIQUE (script_code, organization_id, deleted_flag)
);
CREATE INDEX IF NOT EXISTS idx_script_asset_org_status ON dynamic_script_asset (organization_id, status);

CREATE TABLE IF NOT EXISTS dynamic_script_asset_log (
  id TEXT NOT NULL,
  tenant_id TEXT,
  asset_id TEXT NOT NULL,
  script_code TEXT NOT NULL,
  version INTEGER NOT NULL,
  log_type TEXT NOT NULL,
  snapshot TEXT,
  organization_id TEXT,
  remark TEXT,
  revision INTEGER NOT NULL DEFAULT 0,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_script_asset_log_asset ON dynamic_script_asset_log (asset_id, version);

CREATE TABLE IF NOT EXISTS dynamic_script_binding (
  id TEXT NOT NULL,
  tenant_id TEXT,
  asset_id TEXT NOT NULL,
  asset_version INTEGER,
  form_id TEXT NOT NULL,
  table_code TEXT,
  bind_point TEXT NOT NULL,
  param_mapping TEXT,
  fail_message TEXT,
  execution_order INTEGER,
  enabled TEXT NOT NULL DEFAULT 'Y',
  organization_id TEXT,
  department_id TEXT,
  remark TEXT,
  revision INTEGER NOT NULL DEFAULT 0,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_script_binding_asset ON dynamic_script_binding (asset_id);
CREATE INDEX IF NOT EXISTS idx_script_binding_form ON dynamic_script_binding (form_id, bind_point, enabled);

CREATE TABLE IF NOT EXISTS dynamic_script_exec_log (
  id TEXT NOT NULL,
  asset_id TEXT NOT NULL,
  script_code TEXT NOT NULL,
  version INTEGER,
  bind_point TEXT NOT NULL,
  form_id TEXT,
  table_code TEXT,
  data_id TEXT,
  success TEXT NOT NULL,
  result_summary TEXT,
  error_message TEXT,
  elapsed_ms INTEGER,
  organization_id TEXT,
  created_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_script_exec_asset ON dynamic_script_exec_log (asset_id, version, created_time);
CREATE INDEX IF NOT EXISTS idx_script_exec_trial ON dynamic_script_exec_log (asset_id, version, bind_point, success);

CREATE TABLE IF NOT EXISTS dynamic_share_access_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  organization_id TEXT NOT NULL,
  share_id TEXT,
  form_id TEXT,
  action TEXT NOT NULL,
  token_tail TEXT,
  client_ip TEXT,
  user_agent TEXT,
  result TEXT NOT NULL,
  deny_reason TEXT,
  data_id TEXT,
  operator_id TEXT,
  created_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE INDEX IF NOT EXISTS idx_org_time ON dynamic_share_access_log (organization_id, created_time);
CREATE INDEX IF NOT EXISTS idx_share_time ON dynamic_share_access_log (share_id, created_time);

CREATE TABLE IF NOT EXISTS dynamic_share_access_log_archive (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  organization_id TEXT NOT NULL,
  share_id TEXT,
  form_id TEXT,
  action TEXT NOT NULL,
  token_tail TEXT,
  client_ip TEXT,
  user_agent TEXT,
  result TEXT NOT NULL,
  deny_reason TEXT,
  data_id TEXT,
  created_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE INDEX IF NOT EXISTS idx_org_time ON dynamic_share_access_log_archive (organization_id, created_time);
CREATE INDEX IF NOT EXISTS idx_share_time ON dynamic_share_access_log_archive (share_id, created_time);

CREATE TABLE IF NOT EXISTS dynamic_share_receipt (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  share_id TEXT NOT NULL,
  receipt_digest TEXT NOT NULL,
  data_id TEXT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_share_receipt UNIQUE (share_id, receipt_digest)
);
CREATE INDEX IF NOT EXISTS idx_data ON dynamic_share_receipt (data_id);

CREATE TABLE IF NOT EXISTS flow_definition (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  create_by TEXT,
  update_by TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  flow_code TEXT,
  flow_name TEXT,
  model_value TEXT DEFAULT 'CLASSICS',
  category TEXT,
  version TEXT DEFAULT '1',
  is_publish INTEGER DEFAULT 0,
  form_custom TEXT DEFAULT 'N',
  form_path TEXT,
  activity_status INTEGER DEFAULT 1,
  listener_type TEXT,
  listener_path TEXT,
  ext TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS flow_form (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  create_by TEXT,
  update_by TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  form_code TEXT,
  form_name TEXT,
  version TEXT DEFAULT '1',
  is_publish INTEGER DEFAULT 0,
  form_type INTEGER,
  form_path TEXT,
  form_content TEXT,
  ext TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS flow_form_bind (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  flow_code TEXT NOT NULL,
  flow_name TEXT,
  table_code TEXT NOT NULL,
  form_name TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_code_table_code UNIQUE (flow_code, table_code)
);
CREATE INDEX IF NOT EXISTS idx_ffb_table_code ON flow_form_bind (table_code);

CREATE TABLE IF NOT EXISTS flow_form_btn_perm (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  version_id TEXT NOT NULL,
  flow_code TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  node_code TEXT NOT NULL DEFAULT 'global',
  btn_code TEXT NOT NULL,
  btn_name TEXT,
  alias_name TEXT,
  is_show INTEGER NOT NULL DEFAULT 0,
  show_dialog INTEGER,
  color TEXT,
  sort INTEGER,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ffbp_flow ON flow_form_btn_perm (flow_code, version);
CREATE INDEX IF NOT EXISTS idx_ffbp_node ON flow_form_btn_perm (version_id, node_code);
CREATE INDEX IF NOT EXISTS idx_ffbp_version_id ON flow_form_btn_perm (version_id);

CREATE TABLE IF NOT EXISTS flow_form_field_perm (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  version_id TEXT NOT NULL,
  flow_code TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  node_code TEXT NOT NULL DEFAULT 'global',
  field_code TEXT NOT NULL,
  field_name TEXT,
  is_show INTEGER NOT NULL DEFAULT 1,
  is_edit INTEGER NOT NULL DEFAULT 0,
  is_required INTEGER NOT NULL DEFAULT 0,
  default_type TEXT,
  default_value TEXT,
  dict_type TEXT,
  sort INTEGER,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_fffp_flow ON flow_form_field_perm (flow_code, version);
CREATE INDEX IF NOT EXISTS idx_fffp_node ON flow_form_field_perm (version_id, node_code);
CREATE INDEX IF NOT EXISTS idx_fffp_version_id ON flow_form_field_perm (version_id);

CREATE TABLE IF NOT EXISTS flow_form_node_config (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  definition_id TEXT NOT NULL,
  definition_code TEXT NOT NULL,
  node_code TEXT NOT NULL,
  table_code TEXT,
  field_config TEXT,
  btn_config TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_definition_node UNIQUE (definition_id, node_code)
);
CREATE INDEX IF NOT EXISTS idx_ffnc_definition_code ON flow_form_node_config (definition_code);

CREATE TABLE IF NOT EXISTS flow_form_version (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  flow_code TEXT NOT NULL,
  flow_name TEXT,
  table_code TEXT NOT NULL,
  form_name TEXT,
  version INTEGER NOT NULL DEFAULT 1,
  publish_status INTEGER NOT NULL DEFAULT 0,
  current_flag TEXT NOT NULL DEFAULT 'N',
  publish_time TEXT,
  publish_by TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_version UNIQUE (flow_code, version)
);
CREATE INDEX IF NOT EXISTS idx_ffv_current ON flow_form_version (flow_code, current_flag);
CREATE INDEX IF NOT EXISTS idx_ffv_table_code ON flow_form_version (table_code);

CREATE TABLE IF NOT EXISTS flow_his_task (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  definition_id INTEGER,
  flow_name TEXT,
  instance_id INTEGER,
  task_id INTEGER,
  cooperate_type INTEGER DEFAULT 0,
  business_id TEXT,
  node_code TEXT,
  node_name TEXT,
  node_type INTEGER,
  target_node_code TEXT,
  target_node_name TEXT,
  approver TEXT,
  collaborator TEXT,
  skip_type TEXT,
  flow_status TEXT,
  message TEXT,
  variable TEXT,
  ext TEXT,
  form_custom TEXT DEFAULT 'N',
  form_path TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_his_approver ON flow_his_task (approver);
CREATE INDEX IF NOT EXISTS idx_his_business_id ON flow_his_task (business_id);
CREATE INDEX IF NOT EXISTS idx_his_instance_id ON flow_his_task (instance_id);
CREATE INDEX IF NOT EXISTS idx_his_task_id ON flow_his_task (task_id);

CREATE TABLE IF NOT EXISTS flow_instance (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  create_by TEXT,
  update_by TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  definition_id INTEGER,
  flow_name TEXT,
  business_id TEXT,
  node_type INTEGER,
  node_code TEXT,
  node_name TEXT,
  variable TEXT,
  flow_status TEXT,
  activity_status INTEGER DEFAULT 1,
  form_custom TEXT DEFAULT 'N',
  form_path TEXT,
  def_json TEXT,
  ext TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_instance_business_id ON flow_instance (business_id);
CREATE INDEX IF NOT EXISTS idx_instance_create_by ON flow_instance (create_by);
CREATE INDEX IF NOT EXISTS idx_instance_definition_id ON flow_instance (definition_id);

CREATE TABLE IF NOT EXISTS flow_node (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  create_by TEXT,
  update_by TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  node_type INTEGER,
  definition_id INTEGER,
  node_code TEXT,
  node_name TEXT,
  permission_flag TEXT,
  node_ratio TEXT,
  coordinate TEXT,
  version TEXT DEFAULT '1',
  any_node_skip TEXT,
  listener_type TEXT,
  listener_path TEXT,
  form_custom TEXT DEFAULT 'N',
  form_path TEXT,
  ext TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_node_definition_id ON flow_node (definition_id);

CREATE TABLE IF NOT EXISTS flow_skip (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  create_by TEXT,
  update_by TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  definition_id INTEGER,
  node_id INTEGER,
  now_node_code TEXT,
  now_node_type INTEGER,
  next_node_code TEXT,
  next_node_type INTEGER,
  skip_name TEXT,
  skip_type TEXT,
  skip_condition TEXT,
  coordinate TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_skip_definition_id ON flow_skip (definition_id);
CREATE INDEX IF NOT EXISTS idx_skip_now_node_code ON flow_skip (now_node_code);

CREATE TABLE IF NOT EXISTS flow_task (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  create_by TEXT,
  update_by TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  definition_id INTEGER,
  instance_id INTEGER,
  flow_name TEXT,
  business_id TEXT,
  node_code TEXT,
  node_name TEXT,
  node_type INTEGER,
  flow_status TEXT,
  form_custom TEXT DEFAULT 'N',
  form_path TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_task_definition_id ON flow_task (definition_id);
CREATE INDEX IF NOT EXISTS idx_task_instance_id ON flow_task (instance_id);

CREATE TABLE IF NOT EXISTS flow_user (
  id INTEGER NOT NULL,
  create_time TEXT,
  update_time TEXT,
  create_by TEXT,
  update_by TEXT,
  tenant_id TEXT,
  del_flag TEXT DEFAULT '0',
  type TEXT,
  processed_by TEXT,
  associated INTEGER,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_user_associated ON flow_user (associated);
CREATE INDEX IF NOT EXISTS idx_user_processed_by ON flow_user (processed_by);

CREATE TABLE IF NOT EXISTS jiapei_class_info (
  id TEXT NOT NULL,
  class_code TEXT NOT NULL,
  class_name TEXT NOT NULL,
  grade TEXT NOT NULL,
  head_teacher TEXT,
  classroom TEXT,
  class_status TEXT,
  student_count INTEGER,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uni_class_code UNIQUE (class_code, deleted_time)
);


CREATE TABLE IF NOT EXISTS jiapei_course_apply (
  id TEXT NOT NULL,
  class_ref TEXT NOT NULL,
  course_name TEXT NOT NULL,
  start_time TEXT NOT NULL,
  classroom TEXT NOT NULL,
  teacher_user TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS jiapei_course_info (
  id TEXT NOT NULL,
  course_no TEXT NOT NULL,
  course_name TEXT NOT NULL,
  course_type TEXT,
  class_hours INTEGER NOT NULL,
  teacher_ref TEXT,
  classroom TEXT,
  course_status TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uni_course_no UNIQUE (course_no, deleted_time)
);


CREATE TABLE IF NOT EXISTS jiapei_custom_view_demo (
  id TEXT NOT NULL,
  teacher_no TEXT NOT NULL,
  teacher_name TEXT NOT NULL,
  gender TEXT,
  teach_class TEXT,
  teacher_title TEXT,
  phone TEXT,
  join_date TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uni_teacher_no UNIQUE (teacher_no, deleted_time)
);


CREATE TABLE IF NOT EXISTS jiapei_leave_apply (
  id TEXT NOT NULL,
  leave_no TEXT,
  applicant TEXT,
  student_ref TEXT,
  class_ref TEXT,
  teacher_user TEXT,
  leave_type TEXT NOT NULL,
  start_time TEXT NOT NULL,
  end_time TEXT NOT NULL,
  leave_days INTEGER NOT NULL,
  reason TEXT,
  leave_status TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS jiapei_student_info (
  id TEXT NOT NULL,
  student_no TEXT NOT NULL,
  student_name TEXT NOT NULL,
  gender TEXT,
  class_ref TEXT,
  birth_date TEXT,
  phone TEXT,
  student_status TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uni_student_no UNIQUE (student_no, deleted_time)
);


CREATE TABLE IF NOT EXISTS jiapei_teacher_info (
  id TEXT NOT NULL,
  teacher_no TEXT NOT NULL,
  teacher_name TEXT NOT NULL,
  gender TEXT,
  teach_class TEXT,
  teacher_title TEXT,
  phone TEXT,
  join_date TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uni_teacher_no UNIQUE (teacher_no, deleted_time)
);


CREATE TABLE IF NOT EXISTS jiapei_thing_model (
  id TEXT NOT NULL,
  model_name TEXT NOT NULL,
  model_code TEXT NOT NULL,
  connect_type TEXT NOT NULL,
  industry_type TEXT NOT NULL,
  remark_info TEXT,
  archive_dict TEXT,
  prop_list TEXT,
  action_list TEXT,
  event_list TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uni_model_code UNIQUE (model_code, deleted_time)
);


CREATE TABLE IF NOT EXISTS jiapei_thing_model_action_list (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  action_name TEXT,
  action_code TEXT,
  action_call_method TEXT,
  action_enabled TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS jiapei_thing_model_archive_dict (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  dict_field_name TEXT,
  dict_field_code TEXT,
  dict_data_type TEXT,
  dict_field_type TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS jiapei_thing_model_event_list (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  event_name TEXT,
  event_code TEXT,
  event_type TEXT,
  event_enabled TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS jiapei_thing_model_prop_list (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  prop_name TEXT,
  prop_code TEXT,
  prop_get_method TEXT,
  prop_type TEXT,
  prop_data_type TEXT,
  prop_show TEXT,
  prop_enabled TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_ai_artifact (
  id TEXT NOT NULL,
  tenant_id TEXT,
  session_id TEXT,
  object_type TEXT NOT NULL,
  object_id TEXT NOT NULL,
  code TEXT,
  rolled_back TEXT NOT NULL DEFAULT 'N',
  rollback_note TEXT,
  organization_id TEXT,
  remark TEXT,
  revision INTEGER NOT NULL DEFAULT 0,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ai_artifact_object ON system_ai_artifact (object_type, object_id);
CREATE INDEX IF NOT EXISTS idx_ai_artifact_session ON system_ai_artifact (session_id, rolled_back);

CREATE TABLE IF NOT EXISTS system_ai_config (
  id TEXT NOT NULL,
  tenant_id TEXT,
  organization_id TEXT NOT NULL,
  provider TEXT,
  base_url TEXT NOT NULL,
  api_key_cipher TEXT,
  api_key_prefix TEXT,
  api_key_last4 TEXT,
  model TEXT,
  temperature NUMERIC,
  session_token_limit INTEGER,
  intranet_flag TEXT NOT NULL DEFAULT 'N',
  enabled TEXT NOT NULL DEFAULT 'N',
  remark TEXT,
  revision INTEGER NOT NULL DEFAULT 0,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  PRIMARY KEY (id),
  CONSTRAINT uk_ai_config_org UNIQUE (organization_id, deleted_flag)
);


CREATE TABLE IF NOT EXISTS system_ai_session_log (
  id TEXT NOT NULL,
  session_id TEXT,
  user_id TEXT,
  user_name TEXT,
  organization_id TEXT,
  action TEXT NOT NULL,
  http_method TEXT,
  request_uri TEXT,
  deny_reason TEXT,
  created_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ai_session_log_session ON system_ai_session_log (session_id, created_time);

CREATE TABLE IF NOT EXISTS system_app_common_group (
  id TEXT NOT NULL,
  app_id TEXT NOT NULL,
  common_group_id TEXT NOT NULL,
  status INTEGER NOT NULL,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_app_info (
  id TEXT NOT NULL,
  app_key TEXT NOT NULL,
  app_secret TEXT NOT NULL,
  icon TEXT,
  app_name TEXT,
  code TEXT,
  app_path TEXT,
  pc_path TEXT,
  type TEXT NOT NULL DEFAULT 'CUSTOM',
  user_id TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  sort INTEGER NOT NULL DEFAULT 1,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS user_id_index ON system_app_info (user_id);

CREATE TABLE IF NOT EXISTS system_app_menu (
  id TEXT NOT NULL,
  app_id TEXT NOT NULL,
  menu_id TEXT NOT NULL,
  status INTEGER NOT NULL,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  must_menu INTEGER DEFAULT 0,
  visible_menu INTEGER DEFAULT 1,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS appid_menuid ON system_app_menu (app_id, menu_id);

CREATE TABLE IF NOT EXISTS system_app_path (
  id TEXT NOT NULL,
  app_id TEXT NOT NULL,
  path TEXT NOT NULL,
  status INTEGER NOT NULL,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_app_workflow (
  id TEXT NOT NULL,
  app_id TEXT NOT NULL,
  workflow_form_id TEXT NOT NULL,
  status INTEGER NOT NULL,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  name TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_common_group (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  id TEXT NOT NULL,
  remark TEXT,
  name TEXT NOT NULL,
  code TEXT,
  module_type TEXT DEFAULT 'SYSTEM',
  description TEXT,
  group_type TEXT NOT NULL DEFAULT 'USER_GROUP',
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_common_group_menu (
  id TEXT NOT NULL,
  menu_id TEXT NOT NULL,
  common_group_id TEXT NOT NULL,
  status INTEGER NOT NULL,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  must_menu INTEGER DEFAULT 1,
  visible_menu INTEGER DEFAULT 1,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_default_config (
  id TEXT NOT NULL,
  config_key TEXT NOT NULL,
  config_value TEXT,
  config_name TEXT,
  organization_id TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  remark TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_department (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  dept_code TEXT NOT NULL,
  dept_name TEXT NOT NULL,
  order_num INTEGER DEFAULT 0,
  leader_id TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  organization_id TEXT,
  system_type TEXT NOT NULL DEFAULT 'NORMAL',
  syn_flag INTEGER DEFAULT 0,
  syn_id TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_dept_relation (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  parent_id TEXT NOT NULL,
  dept_id TEXT NOT NULL,
  level INTEGER NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_dictionary_data (
  id TEXT NOT NULL,
  dict_sort INTEGER NOT NULL DEFAULT 0,
  dict_label TEXT NOT NULL,
  dict_value TEXT NOT NULL,
  dict_type TEXT NOT NULL,
  css_class TEXT,
  list_class TEXT,
  is_default TEXT DEFAULT 'N',
  status INTEGER NOT NULL DEFAULT 0,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  remark TEXT,
  organization_id TEXT,
  department_id TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_dictionary_type (
  id TEXT NOT NULL,
  dict_name TEXT NOT NULL,
  dict_type TEXT NOT NULL,
  status INTEGER NOT NULL DEFAULT 0,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  remark TEXT,
  organization_id TEXT,
  department_id TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_file_config (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT NOT NULL,
  name TEXT NOT NULL DEFAULT 'INIT',
  type TEXT NOT NULL DEFAULT 'IMAGE',
  endpoint TEXT,
  access_id TEXT,
  access_key TEXT DEFAULT 'LOCAL',
  bucket TEXT,
  store_path TEXT,
  net_path TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_file_info (
  id TEXT NOT NULL DEFAULT 'GLOBAL',
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  source_id TEXT NOT NULL DEFAULT '1',
  source_stage TEXT NOT NULL DEFAULT 'INIT',
  type TEXT NOT NULL DEFAULT 'IMAGE',
  image_path TEXT NOT NULL,
  image_url TEXT NOT NULL,
  original_name TEXT NOT NULL,
  new_name TEXT NOT NULL,
  file_size INTEGER NOT NULL,
  file_store_type TEXT DEFAULT 'LOCAL',
  module TEXT NOT NULL DEFAULT 'GLOBAL',
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_log_login (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  id TEXT NOT NULL,
  remark TEXT,
  login_name TEXT NOT NULL,
  nick_name TEXT NOT NULL,
  login_ip TEXT NOT NULL,
  login_location TEXT,
  browser TEXT,
  os TEXT,
  platform TEXT,
  message TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_log_operation (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  id TEXT NOT NULL,
  remark TEXT,
  name TEXT NOT NULL,
  operation_type TEXT NOT NULL,
  method TEXT,
  request_method TEXT,
  business_type TEXT,
  operation_url TEXT,
  operation_ip TEXT,
  operation_location TEXT,
  operation_param TEXT,
  operation_result TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  operation_person TEXT,
  message TEXT,
  organization_id TEXT,
  department_id TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_menu (
  id TEXT NOT NULL,
  menu_name TEXT NOT NULL,
  menu_code TEXT,
  order_num INTEGER NOT NULL DEFAULT 1,
  visible INTEGER DEFAULT 1,
  path TEXT,
  menu_type TEXT,
  permission TEXT,
  status INTEGER DEFAULT 1,
  icon TEXT,
  app_menu INTEGER DEFAULT 0,
  component TEXT,
  params TEXT,
  is_frame INTEGER,
  is_cache INTEGER,
  parent_id TEXT NOT NULL,
  organization_id TEXT NOT NULL DEFAULT '16663888968888889',
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  remark TEXT,
  deleted_time TEXT,
  deleted_by TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_message (
  id TEXT NOT NULL,
  main_title TEXT NOT NULL,
  sub_title TEXT,
  message_type TEXT NOT NULL DEFAULT 'NOTICE',
  message_person TEXT NOT NULL DEFAULT 'SEL',
  detail_url TEXT,
  content TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  remark TEXT,
  organization_id TEXT,
  department_id TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  created_name TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_message_user (
  id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  status INTEGER NOT NULL DEFAULT 0,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  nick_name TEXT,
  PRIMARY KEY (id),
  CONSTRAINT unique_user_message UNIQUE (message_id, user_id)
);


CREATE TABLE IF NOT EXISTS system_notice (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  id TEXT NOT NULL,
  remark TEXT,
  notice_title TEXT,
  notice_type TEXT,
  content TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  detail_url TEXT,
  organization_id TEXT,
  department_id TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_openapi_call_log (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT,
  organization_id TEXT,
  remark TEXT,
  request_id TEXT,
  credential_id TEXT,
  app_id TEXT,
  access_key TEXT,
  endpoint TEXT,
  http_method TEXT,
  action TEXT,
  table_code TEXT,
  biz_id TEXT,
  result TEXT NOT NULL DEFAULT 'SUCCESS',
  status_code INTEGER,
  error_code TEXT,
  error_msg TEXT,
  cost_ms INTEGER,
  client_ip TEXT,
  nonce TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_call_log_ak_time ON system_openapi_call_log (access_key, created_time);
CREATE INDEX IF NOT EXISTS idx_call_log_cred ON system_openapi_call_log (credential_id);
CREATE INDEX IF NOT EXISTS idx_call_log_org_time ON system_openapi_call_log (organization_id, created_time);

CREATE TABLE IF NOT EXISTS system_openapi_credential (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  app_id TEXT NOT NULL,
  credential_name TEXT NOT NULL,
  access_key TEXT NOT NULL,
  secret_cipher TEXT NOT NULL,
  secret_fingerprint TEXT NOT NULL,
  secret_prefix TEXT,
  secret_last4 TEXT,
  scopes TEXT,
  table_grant TEXT,
  bound_user_id TEXT,
  bound_department_id TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  expire_time TEXT,
  rate_limit_per_minute INTEGER DEFAULT 600,
  last_call_time TEXT,
  call_count INTEGER DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_openapi_access_key UNIQUE (access_key)
);
CREATE INDEX IF NOT EXISTS idx_openapi_cred_app ON system_openapi_credential (app_id);
CREATE INDEX IF NOT EXISTS idx_openapi_cred_org ON system_openapi_credential (organization_id);

CREATE TABLE IF NOT EXISTS system_organization (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision TEXT NOT NULL DEFAULT '1',
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  remark TEXT,
  organization_name TEXT,
  order_num INTEGER NOT NULL DEFAULT 1,
  leader_id TEXT,
  status INTEGER DEFAULT 1,
  organization_code TEXT NOT NULL,
  syn_type INTEGER DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT unique_code UNIQUE (organization_code)
);


CREATE TABLE IF NOT EXISTS system_organization_app (
  id TEXT NOT NULL,
  app_id TEXT NOT NULL,
  status INTEGER DEFAULT 0,
  organization_id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_time TEXT,
  deleted_by TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  remark TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_organization_menu (
  id TEXT NOT NULL,
  menu_id TEXT NOT NULL,
  app_id TEXT,
  status INTEGER DEFAULT 1,
  organization_id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_time TEXT,
  deleted_by TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  remark TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS organization_menu ON system_organization_menu (menu_id, organization_id);

CREATE TABLE IF NOT EXISTS system_position (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  position_name TEXT NOT NULL,
  position_code TEXT NOT NULL,
  status INTEGER DEFAULT 0,
  order_num INTEGER DEFAULT 1,
  organization_id TEXT,
  department_id TEXT,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_role (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  remark TEXT,
  data_scope INTEGER DEFAULT 20,
  role_key TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  order_num INTEGER NOT NULL DEFAULT 1,
  role_name TEXT NOT NULL,
  classification_id TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  system_type TEXT NOT NULL DEFAULT 'NORMAL',
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_role_group (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  group_name TEXT NOT NULL,
  group_type TEXT NOT NULL DEFAULT 'CUSTOM',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_role_group_role (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  id TEXT NOT NULL,
  remark TEXT,
  group_id TEXT NOT NULL,
  role_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_role_menu (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  role_id TEXT NOT NULL,
  menu_id TEXT NOT NULL,
  permission TEXT NOT NULL DEFAULT '',
  data_scope INTEGER DEFAULT 20,
  app_id TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS role_menu_appid_idx ON system_role_menu (role_id, menu_id, app_id);

CREATE TABLE IF NOT EXISTS system_role_user (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  role_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT role_user_idx UNIQUE (role_id, user_id)
);


CREATE TABLE IF NOT EXISTS system_role_user_dept (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  role_user_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  auth_way INTEGER DEFAULT 1,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_schedule_instance (
  instance_id TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'WORKER',
  lease_until TEXT NOT NULL,
  start_time TEXT NOT NULL,
  stat_json TEXT,
  PRIMARY KEY (instance_id)
);
CREATE INDEX IF NOT EXISTS idx_lease ON system_schedule_instance (lease_until);

CREATE TABLE IF NOT EXISTS system_schedule_lock (
  lock_key TEXT NOT NULL,
  holder TEXT,
  lease_until TEXT NOT NULL DEFAULT '1970-01-01 00:00:00.000',
  version INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (lock_key)
);


CREATE TABLE IF NOT EXISTS system_schedule_log_archive (
  id INTEGER NOT NULL,
  task_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  fire_time TEXT NOT NULL,
  attempt INTEGER NOT NULL,
  status TEXT NOT NULL,
  error_msg TEXT,
  result_digest TEXT,
  archived_time TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_task ON system_schedule_log_archive (task_id, fire_time);
CREATE INDEX IF NOT EXISTS idx_time ON system_schedule_log_archive (archived_time);

CREATE TABLE IF NOT EXISTS system_schedule_metric_snapshot (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  metric_name TEXT NOT NULL,
  tag_outcome TEXT,
  statistic TEXT NOT NULL,
  value REAL NOT NULL,
  snap_time TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_name_time ON system_schedule_metric_snapshot (metric_name, snap_time);

CREATE TABLE IF NOT EXISTS system_schedule_task (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT NOT NULL DEFAULT '1970-01-01 00:00:00.000',
  department_id TEXT NOT NULL DEFAULT '',
  organization_id TEXT NOT NULL,
  remark TEXT,
  task_code TEXT,
  title TEXT NOT NULL,
  app_id TEXT NOT NULL DEFAULT '',
  source TEXT NOT NULL,
  biz_key TEXT NOT NULL DEFAULT '',
  trigger_type TEXT NOT NULL,
  cron_expr TEXT,
  interval_seconds INTEGER,
  period_seconds INTEGER,
  jitter_seconds INTEGER NOT NULL DEFAULT 0,
  misfire_policy TEXT NOT NULL DEFAULT 'COMPENSATE',
  misfire_window_seconds INTEGER NOT NULL DEFAULT 300,
  concurrent_policy TEXT NOT NULL DEFAULT 'FORBID',
  timeout_seconds INTEGER NOT NULL DEFAULT 300,
  max_retry INTEGER NOT NULL DEFAULT 3,
  action_type TEXT NOT NULL,
  action_config TEXT,
  biz_context TEXT,
  database_name TEXT NOT NULL DEFAULT 'master',
  callback_url TEXT,
  status TEXT NOT NULL DEFAULT 'READY',
  next_fire_time TEXT,
  pending_next_fire_time TEXT,
  pending_change TEXT,
  last_fire_time TEXT,
  owner_instance TEXT,
  lease_until TEXT,
  running_instance TEXT,
  running_since TEXT,
  trigger_count INTEGER NOT NULL DEFAULT 0,
  fail_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  current_attempt INTEGER NOT NULL DEFAULT 1,
  version INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_biz UNIQUE (organization_id, source, biz_key, deleted_time),
  CONSTRAINT uk_code UNIQUE (app_id, task_code, deleted_time)
);
CREATE INDEX IF NOT EXISTS idx_lease ON system_schedule_task (status, lease_until);
CREATE INDEX IF NOT EXISTS idx_org ON system_schedule_task (organization_id, app_id, source, status);
CREATE INDEX IF NOT EXISTS idx_running ON system_schedule_task (status, running_since);
CREATE INDEX IF NOT EXISTS idx_trigger ON system_schedule_task (deleted_flag, status, next_fire_time);

CREATE TABLE IF NOT EXISTS system_schedule_task_log (
  id INTEGER PRIMARY KEY,
  task_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  app_id TEXT NOT NULL DEFAULT '',
  source TEXT NOT NULL,
  fire_time TEXT NOT NULL,
  attempt INTEGER NOT NULL DEFAULT 1,
  task_version INTEGER NOT NULL,
  action_snapshot TEXT,
  status TEXT NOT NULL,
  executor TEXT,
  start_time TEXT,
  end_time TEXT,
  cost_ms INTEGER,
  error_msg TEXT,
  result_digest TEXT,
  trace_id TEXT,
  CONSTRAINT uk_idem UNIQUE (task_id, fire_time, attempt)
);
CREATE INDEX IF NOT EXISTS idx_org_status ON system_schedule_task_log (organization_id, status, fire_time);

CREATE TABLE IF NOT EXISTS system_schedule_task_queue (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_id TEXT NOT NULL,
  fire_time TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  queued_time TEXT NOT NULL,
  CONSTRAINT uk_task_fire UNIQUE (task_id, fire_time)
);
CREATE INDEX IF NOT EXISTS idx_poll ON system_schedule_task_queue (task_id, status, fire_time);

CREATE TABLE IF NOT EXISTS system_social_app_config (
  id TEXT NOT NULL,
  app_id TEXT,
  secret TEXT,
  source TEXT,
  app_code TEXT NOT NULL,
  app_name TEXT NOT NULL,
  app_url TEXT,
  examine_user TEXT DEFAULT 'N',
  agent_id TEXT,
  corp_id TEXT,
  scheme TEXT,
  role_id TEXT,
  remark TEXT,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  syn_employee INTEGER,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_social_user (
  id TEXT NOT NULL,
  uuid TEXT,
  source TEXT,
  app_code TEXT,
  access_token TEXT,
  expire_in INTEGER,
  refresh_token TEXT,
  open_id TEXT,
  uid TEXT,
  access_code TEXT,
  union_id TEXT,
  scope TEXT,
  token_type TEXT,
  id_token TEXT,
  mac_algorithm TEXT,
  mac_key TEXT,
  code TEXT,
  oauth_token TEXT,
  oauth_token_secret TEXT,
  remark TEXT,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_sso_oidc_config (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  config_code TEXT NOT NULL,
  config_name TEXT NOT NULL,
  icon TEXT,
  issuer TEXT,
  discovery_url TEXT,
  authorize_url TEXT,
  token_url TEXT,
  userinfo_url TEXT,
  jwks_uri TEXT,
  client_id TEXT NOT NULL,
  client_secret_cipher TEXT,
  client_secret_prefix TEXT,
  client_secret_last4 TEXT,
  scopes TEXT DEFAULT 'openid profile email',
  redirect_uri TEXT,
  claim_mapping TEXT,
  auto_register INTEGER NOT NULL DEFAULT 0,
  default_role_ids TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uk_sso_config_code UNIQUE (config_code)
);
CREATE INDEX IF NOT EXISTS idx_sso_config_org ON system_sso_oidc_config (organization_id);

CREATE TABLE IF NOT EXISTS system_user_app_menu (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  menu_id TEXT NOT NULL,
  status INTEGER NOT NULL,
  remark TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_user_base (
  id TEXT NOT NULL,
  login_name TEXT NOT NULL,
  nick_name TEXT NOT NULL,
  user_type TEXT NOT NULL DEFAULT 'default',
  email TEXT,
  phone TEXT,
  password TEXT NOT NULL,
  password_v2 TEXT,
  gender TEXT DEFAULT '2',
  avatar TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  identity_num TEXT,
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  deleted_time TEXT,
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  remark TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS login_status_index ON system_user_base (login_name, status, organization_id, department_id);

CREATE TABLE IF NOT EXISTS system_user_department (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision TEXT NOT NULL DEFAULT '1',
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  user_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  is_main INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_user_group (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  id TEXT NOT NULL,
  remark TEXT,
  group_code TEXT,
  group_name TEXT NOT NULL,
  group_type TEXT NOT NULL DEFAULT 'SYSTEM',
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  common_group_id TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_user_group_user (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  id TEXT NOT NULL,
  remark TEXT,
  group_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_user_position (
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision TEXT NOT NULL DEFAULT '1',
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  id TEXT NOT NULL,
  remark TEXT,
  user_id TEXT NOT NULL,
  position_id TEXT NOT NULL,
  is_main INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS system_user_service_org (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  department_id TEXT NOT NULL,
  data_scope INTEGER NOT NULL DEFAULT 20,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  remark TEXT,
  PRIMARY KEY (id),
  CONSTRAINT user_org_unique UNIQUE (user_id, organization_id)
);


CREATE TABLE IF NOT EXISTS system_user_social_user (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  social_user_id TEXT NOT NULL,
  remark TEXT,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_by TEXT,
  deleted_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS social_user_index ON system_user_social_user (user_id, social_user_id);

CREATE TABLE IF NOT EXISTS system_webhook_delivery_log (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT,
  organization_id TEXT,
  remark TEXT,
  event_id TEXT NOT NULL,
  subscription_id TEXT,
  attempt INTEGER DEFAULT 1,
  url TEXT,
  status_code INTEGER,
  success INTEGER DEFAULT 0,
  response_brief TEXT,
  error_msg TEXT,
  cost_ms INTEGER,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_webhook_del_event ON system_webhook_delivery_log (event_id);
CREATE INDEX IF NOT EXISTS idx_webhook_del_sub_time ON system_webhook_delivery_log (subscription_id, created_time);

CREATE TABLE IF NOT EXISTS system_webhook_event (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT,
  created_time TEXT,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT,
  organization_id TEXT,
  remark TEXT,
  subscription_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  table_code TEXT,
  biz_id TEXT,
  payload TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  retry_count INTEGER DEFAULT 0,
  next_retry_time TEXT,
  last_error TEXT,
  delivered_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_webhook_evt_org ON system_webhook_event (organization_id, event_type);
CREATE INDEX IF NOT EXISTS idx_webhook_evt_scan ON system_webhook_event (status, next_retry_time);
CREATE INDEX IF NOT EXISTS idx_webhook_evt_sub ON system_webhook_event (subscription_id);

CREATE TABLE IF NOT EXISTS system_webhook_subscription (
  id TEXT NOT NULL,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  app_id TEXT,
  subscription_name TEXT NOT NULL,
  url TEXT NOT NULL,
  http_method TEXT DEFAULT 'POST',
  secret_cipher TEXT,
  secret_prefix TEXT,
  secret_last4 TEXT,
  events TEXT,
  table_code TEXT,
  headers TEXT,
  ssl_verify INTEGER NOT NULL DEFAULT 1,
  timeout_ms INTEGER DEFAULT 5000,
  max_retry INTEGER DEFAULT 5,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_webhook_sub_org ON system_webhook_subscription (organization_id);
CREATE INDEX IF NOT EXISTS idx_webhook_sub_table ON system_webhook_subscription (table_code);

CREATE TABLE IF NOT EXISTS thing_model (
  id TEXT NOT NULL,
  model_name TEXT NOT NULL,
  model_code TEXT NOT NULL,
  connect_type TEXT NOT NULL,
  industry_type TEXT NOT NULL,
  remark_info TEXT,
  archive_dict TEXT,
  prop_list TEXT,
  action_list TEXT,
  event_list TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  deleted_by TEXT,
  deleted_time TEXT,
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  remark TEXT,
  title TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT uni_model_code UNIQUE (model_code, deleted_time)
);


CREATE TABLE IF NOT EXISTS thing_model_action_list (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  action_name TEXT,
  action_code TEXT,
  action_call_method TEXT,
  action_enabled TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS thing_model_archive_dict (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  dict_field_name TEXT,
  dict_field_code TEXT,
  dict_data_type TEXT,
  dict_field_type TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS thing_model_event_list (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  event_name TEXT,
  event_code TEXT,
  event_type TEXT,
  event_enabled TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS thing_model_prop_list (
  id TEXT NOT NULL,
  parent_id TEXT NOT NULL,
  prop_name TEXT,
  prop_code TEXT,
  prop_get_method TEXT,
  prop_type TEXT,
  prop_data_type TEXT,
  prop_show TEXT,
  prop_enabled TEXT,
  tenant_id TEXT NOT NULL DEFAULT 'system-default',
  revision INTEGER NOT NULL DEFAULT 1,
  created_by TEXT NOT NULL,
  created_time TEXT NOT NULL,
  updated_by TEXT,
  updated_time TEXT,
  deleted_flag TEXT NOT NULL DEFAULT 'N',
  department_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS zz_menu (
  id TEXT
);

-- ----------------------------
-- 数据库结构变更台账（ROADMAP 1.14）：init 基线之后的增量脚本在此登记，终结升级脚本漂移。
-- 流程见 sql/README.md「迁移台账」节：apply 前先核对脚本 SHA-256，apply 后 INSERT 登记。
-- ----------------------------
CREATE TABLE IF NOT EXISTS platform_schema_history (
  id TEXT NOT NULL,
  version TEXT NOT NULL,
  script_name TEXT NOT NULL,
  dialect TEXT NOT NULL,
  checksum TEXT,
  applied_by TEXT NOT NULL DEFAULT 'manual',
  applied_at TEXT NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_schema_script ON platform_schema_history (script_name, dialect);

INSERT INTO platform_schema_history
VALUES ('10000000000000000001', 'baseline', 'init-sqlite.sql 全量基线（与 init.sql 同口径）', 'sqlite', NULL, 'init', '2026-11-08 19:00:00');

-- ============================================================
-- 最小种子数据段与 AI 问数对话表（由 sql/init.sql 转换生成，表名同上方 DDL，
-- 口径见 deployment-guide 4.1；INSERT 语句为 SQLite 方言，变量与函数已内联等价替换）
-- ============================================================

CREATE TABLE IF NOT EXISTS system_ai_chat_message (
  id TEXT NOT NULL,
  session_id TEXT,
  user_id TEXT,
  user_name TEXT,
  organization_id TEXT,
  role TEXT NOT NULL,
  content TEXT,
  charts TEXT,
  created_time TEXT,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_message_session ON system_ai_chat_message (session_id, created_time);
CREATE INDEX IF NOT EXISTS idx_ai_chat_message_user ON system_ai_chat_message (user_id, created_time);
BEGIN TRANSACTION;
INSERT INTO `system_organization` VALUES ('16663888968888889','system-default','1','188666666666','2022-11-16 14:41:05','188666666666','2023-04-17 14:58:23','N',NULL,NULL,'不要删除。','系统企业',1,'188666666666',1,'default',0);
INSERT INTO `system_department` VALUES ('system-default',1,'188666666666','2022-11-23 17:01:04',NULL,NULL,'N',NULL,NULL,'155555555555','','','开发部',0,'188666666666',1,'16663888968888889','NORMAL',0,NULL),('system-default',1,'188666666666','2022-11-25 13:33:33','188666666666','2024-07-18 11:19:21','N',NULL,NULL,'16663888968888889','','','工程部',8,'',1,'16663888968888889','SYSTEM',0,NULL),('system-default',1,'188666666666','2023-09-14 11:55:22',NULL,NULL,'N',NULL,NULL,'1702169136598220802','','','设计组',0,'',1,'16663888968888889','NORMAL',0,NULL),('system-default',1,'188666666666','2023-09-25 15:49:54',NULL,NULL,'N',NULL,NULL,'1706214426871541762','','','项目实施部',0,'1677150163900211202',1,'16663888968888889','NORMAL',0,NULL);
INSERT INTO `system_user_base` VALUES ('188666666666','admin','系统管理员','default',NULL,NULL,'$2a$10$SRK9EBHXmAmqPCQ7BtNkN.guFmO6bkcvD6iM/bvWFuA1.xNqR0cNy',NULL,'1',NULL,1,NULL,'16663888968888889','155555555555','system-default',NULL,1,'188666666666','2022-11-06 14:49:29','188666666666','2024-02-28 15:43:08','N',NULL,NULL);
INSERT INTO `system_role` VALUES ('12263522227777777','system-default',1,'188666666666','2022-11-18 16:22:15',NULL,NULL,'N',NULL,NULL,NULL,20,'SuperRole',1,1,'组织管理员','1','155555555555','16663888968888889','SYSTEM'),('12263567888888','system-default',1,'188666666666','2023-12-01 16:22:15',NULL,NULL,'N',NULL,NULL,NULL,20,'SuperPlatformRole',1,1,'平台管理员','1','155555555555','16663888968888889','SYSTEM');
INSERT INTO `system_role_user` VALUES ('system-default',1,'188666666666','2022-11-18 16:44:39','188666666666','2022-12-01 10:39:08','N','188666666666','2022-12-01 10:39:08','1',NULL,'12263522227777777','188666666666'),('system-default',1,'188666666666','2022-11-18 16:44:39','188666666666','2022-12-01 10:39:08','N','188666666666','2022-12-01 10:39:08','66887799',NULL,'12263567888888','188666666666');
INSERT INTO `system_user_service_org` VALUES ('1114933510994120716','188666666666','16663888968888889','155555555555',50,'system-default',1,'188666666666','2022-11-18 16:36:44',NULL,NULL,'N',NULL,NULL,NULL);
INSERT INTO `system_app_info` VALUES ('1768482874137485238','PLATFORM','PLATFORM','evaluation','平台管理','PLATFORM',NULL,'system','SYSTEM','',1,30,NULL,'system-default',1,'188666666666','2024-03-15 11:42:29','188666666666','2024-06-06 14:43:01','N',NULL,NULL,'16663888968888889'),('1768482874137485248','SYSTEM','SYSTEM','evaluation','系统管理','SYSTEM',NULL,'system','SYSTEM','',1,25,NULL,'system-default',1,'188666666666','2024-03-15 11:42:29','188666666666','2024-06-07 15:28:30','N',NULL,NULL,'16663888968888889');
INSERT INTO `system_menu` VALUES ('12fe5bef6f2511ee983f00163e085602','菜单管理','system_menu',140,1,'/system/menu','C','system:menu:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51','188666666666','2024-06-07 15:22:41',NULL,NULL,NULL,'N'),('12fe82156f2511ee983f00163e085602','菜单列表','',1,1,'','F','system:menu:list',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fe82166f2511ee983f00163e085602','菜单详情','',1,1,'','F','system:menu:query',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fea83a6f2511ee983f00163e085602','菜单新增','',1,1,'','F','system:menu:add',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fea83b6f2511ee983f00163e085602','菜单修改','',1,1,'','F','system:menu:edit',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fece606f2511ee983f00163e085602','菜单删除','',1,1,'','F','system:menu:remove',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fef4856f2511ee983f00163e085602','菜单导入','',1,1,'','F','system:menu:import',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('12fef4866f2511ee983f00163e085602','菜单导出','',1,1,'','F','system:menu:export',1,'icon-default',0,'',NULL,0,0,'12fe5bef6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:51',NULL,NULL,NULL,NULL,NULL,'N'),('1391b0aebff811f18fc3a5e694323b94','联动规则配置','dynamic_form_linkage',81,1,NULL,'F','dynamic:form:linkage',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'高级设置的联动规则管理',NULL,NULL,'N'),('1391b176bff811f18fc3a5e694323b94','表单外链配置','dynamic_form_share',82,1,NULL,'F','dynamic:form:share',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'高级设置的表单外链管理',NULL,NULL,'N'),('1391b1bcbff811f18fc3a5e694323b94','导入导出配置','dynamic_form_io_config',84,1,NULL,'F','dynamic:form:io:config',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'高级设置的导入导出策略配置',NULL,NULL,'N'),('1391b1dabff811f18fc3a5e694323b94','数据打印','dynamic_data_print',85,1,NULL,'F','dynamic:data:print',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'数据级：按模板打印数据',NULL,NULL,'N'),('1391b1eebff811f18fc3a5e694323b94','数据导入','dynamic_data_import',86,1,NULL,'F','dynamic:data:import',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'数据级：Excel 导入数据',NULL,NULL,'N'),('1391b20cbff811f18fc3a5e694323b94','数据导出','dynamic_data_export',87,1,NULL,'F','dynamic:data:export',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-04 21:32:42',NULL,NULL,'数据级：导出数据 Excel（脱敏+审计）',NULL,NULL,'N'),('1663888968294588891','系统管理','system',500,1,'/system','M','system:system:list',1,'carbon:cloud-service-management',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2022-11-10 16:33:44',NULL,NULL,NULL,NULL,NULL,'N'),('1798986040628969473','平台管理','platform',100,1,'/platform','M','',1,'carbon:cloud-service-management',0,'basic','',0,0,'0','16663888968888889','system-default',1,'188666666666','2024-06-07 15:51:10','188666666666','2024-06-20 15:37:50',NULL,NULL,NULL,'N'),('1803694748176957442','系统管理','org',66,1,'/org','M','',1,'carbon:column-dependency',0,'basic','',0,0,'0','16663888968888889','system-default',1,'188666666666','2024-06-07 15:51:10','188666666666','2024-06-20 15:48:41',NULL,NULL,NULL,'N'),('1814169974018928641','系统日志','system_log',30,1,'','M','',1,'tabler:logs',0,'basic','',0,0,'0','16663888968888889','system-default',1,'188666666666','2024-07-19 13:26:42','188666666666','2026-10-06 22:14:28',NULL,NULL,NULL,'N'),('1866100000000000001','流程管理','flow',150,1,'/flow','M','',1,'icon-default',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,'基于warm-flow的工作流',NULL,NULL,'N'),('1866100000000000002','流程定义','flow_definition',1,1,'/flow/definition','C','flow:definition:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000003','流程设计器','flow_designer',2,0,'/flow/designer','C','flow:definition:query',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,'warm-flow设计器（隐藏菜单，从流程定义进入）',NULL,NULL,'N'),('1866100000000000004','流程表单配置','flow_form-config',3,1,'/flow/form-config','C','flow:form:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,'流程与动态表单绑定及节点字段权限',NULL,NULL,'N'),('1866100000000000005','待办任务','flow_todo',4,1,'/flow/todo','C','flow:task:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000006','已办任务','flow_done',5,1,'/flow/done','C','flow:task:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000007','我发起的','flow_instance',6,1,'/flow/instance','C','flow:instance:list',1,'icon-default',0,'self',NULL,0,0,'1866100000000000001','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000011','流程定义查询','',1,1,'','F','flow:definition:query',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000012','流程定义新增','',2,1,'','F','flow:definition:add',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000013','流程定义修改','',3,1,'','F','flow:definition:edit',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000014','流程定义删除','',4,1,'','F','flow:definition:remove',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000015','流程定义发布/停用','',5,1,'','F','flow:definition:publish',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000016','流程定义导入导出','',6,1,'','F','flow:definition:import',1,'icon-default',0,'',NULL,0,0,'1866100000000000002','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000021','流程表单查询','',1,1,'','F','flow:form:query',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000022','流程表单新增','',2,1,'','F','flow:form:add',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000023','流程表单修改','',3,1,'','F','flow:form:edit',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000024','流程表单删除','',4,1,'','F','flow:form:remove',1,'icon-default',0,'',NULL,0,0,'1866100000000000004','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000031','任务通过','',1,1,'','F','flow:task:approve',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000032','任务驳回','',2,1,'','F','flow:task:reject',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000033','任务转办','',3,1,'','F','flow:task:transfer',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000034','任务委派','',4,1,'','F','flow:task:delegate',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000035','任务加减签','',5,1,'','F','flow:task:addSign',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000036','任务跳转','',6,1,'','F','flow:task:jump',1,'icon-default',0,'',NULL,0,0,'1866100000000000005','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000041','实例撤销','',1,1,'','F','flow:instance:revoke',1,'icon-default',0,'',NULL,0,0,'1866100000000000007','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('1866100000000000042','实例终止','',2,1,'','F','flow:instance:terminate',1,'icon-default',0,'',NULL,0,0,'1866100000000000007','16663888968888889','system-default',1,'188666666666','2026-08-16 00:00:00',NULL,NULL,NULL,NULL,NULL,'N'),('24d3c6e26f2511ee983f00163e085602','通知','system_notice',70,1,'/system/notice','C','system:notice:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21','188666666666','2024-06-20 15:44:53',NULL,NULL,NULL,'N'),('24d3ed076f2511ee983f00163e085602','通知列表','',1,1,'','F','system:notice:list',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d4132d6f2511ee983f00163e085602','通知详情','',1,1,'','F','system:notice:query',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d4132e6f2511ee983f00163e085602','通知新增','',1,1,'','F','system:notice:add',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d439536f2511ee983f00163e085602','通知修改','',1,1,'','F','system:notice:edit',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d439546f2511ee983f00163e085602','通知删除','',1,1,'','F','system:notice:remove',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d45f786f2511ee983f00163e085602','通知导入','',1,1,'','F','system:notice:import',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('24d45f796f2511ee983f00163e085602','通知导出','',1,1,'','F','system:notice:export',1,'icon-default',0,'',NULL,0,0,'24d3c6e26f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:21',NULL,NULL,NULL,NULL,NULL,'N'),('26bb3ab0dad111ee9b3d00163e085602','公共组','system_common-group',155,1,'/system/common-group','C','system:commonGroup:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12','188666666666','2024-06-07 15:22:02',NULL,NULL,NULL,'N'),('26bb86fcdad111ee9b3d00163e085602','公共组列表','',1,1,'','F','system:commonGroup:list',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbad22dad111ee9b3d00163e085602','公共组详情','',1,1,'','F','system:commonGroup:query',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbd348dad111ee9b3d00163e085602','公共组新增','',1,1,'','F','system:commonGroup:add',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbf96edad111ee9b3d00163e085602','公共组修改','',1,1,'','F','system:commonGroup:edit',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bbf96fdad111ee9b3d00163e085602','公共组删除','',1,1,'','F','system:commonGroup:remove',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bc1f94dad111ee9b3d00163e085602','公共组导入','',1,1,'','F','system:commonGroup:import',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('26bc45badad111ee9b3d00163e085602','公共组导出','',1,1,'','F','system:commonGroup:export',1,'icon-default',0,'',NULL,0,0,'26bb3ab0dad111ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-05 17:17:12',NULL,NULL,NULL,NULL,NULL,'N'),('304d18026f2511ee983f00163e085602','企业管理','system_organization',200,1,'/system/organization','C','system:organization:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:40','188666666666','2024-06-07 15:18:53',NULL,NULL,NULL,'N'),('304d3e276f2511ee983f00163e085602','企业列表','',1,1,'','F','system:organization:list',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d644d6f2511ee983f00163e085602','企业详情','',1,1,'','F','system:organization:query',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d644e6f2511ee983f00163e085602','企业新增','',1,1,'','F','system:organization:add',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d8a736f2511ee983f00163e085602','企业修改','',1,1,'','F','system:organization:edit',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304d8a746f2511ee983f00163e085602','企业删除','',1,1,'','F','system:organization:remove',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304db0986f2511ee983f00163e085602','企业导入','',1,1,'','F','system:organization:import',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('304dd6be6f2511ee983f00163e085602','企业导出','',1,1,'','F','system:organization:export',1,'icon-default',0,'',NULL,0,0,'304d18026f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:46:41',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2a65b6f2511ee983f00163e085602','组织拥有的菜单','system_organization-menu',1,1,'/system/organization-menu','C','system:organizationMenu:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01','188666666666','2024-06-07 15:19:50',NULL,NULL,NULL,'N'),('3ca2cc816f2511ee983f00163e085602','组织拥有的菜单列表','',1,1,'','F','system:organizationMenu:list',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2cc826f2511ee983f00163e085602','组织拥有的菜单详情','',1,1,'','F','system:organizationMenu:query',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2f2a76f2511ee983f00163e085602','组织拥有的菜单新增','',1,1,'','F','system:organizationMenu:add',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca2f2a86f2511ee983f00163e085602','组织拥有的菜单修改','',1,1,'','F','system:organizationMenu:edit',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca318cc6f2511ee983f00163e085602','组织拥有的菜单删除','',1,1,'','F','system:organizationMenu:remove',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca33ef26f2511ee983f00163e085602','组织拥有的菜单导入','',1,1,'','F','system:organizationMenu:import',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('3ca33ef36f2511ee983f00163e085602','组织拥有的菜单导出','',1,1,'','F','system:organizationMenu:export',1,'icon-default',0,'',NULL,0,0,'3ca2a65b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:01',NULL,NULL,NULL,NULL,NULL,'N'),('42c53b736f2511ee983f00163e085602','岗位','system_position',80,1,'/system/position','C','system:position:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:11','188666666666','2024-06-20 15:44:42',NULL,NULL,NULL,'N'),('42c561996f2511ee983f00163e085602','岗位列表','',1,1,'','F','system:position:list',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:11',NULL,NULL,NULL,NULL,NULL,'N'),('42c5619a6f2511ee983f00163e085602','岗位详情','',1,1,'','F','system:position:query',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:11',NULL,NULL,NULL,NULL,NULL,'N'),('42c587be6f2511ee983f00163e085602','岗位新增','',1,1,'','F','system:position:add',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c587bf6f2511ee983f00163e085602','岗位修改','',1,1,'','F','system:position:edit',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c5ade46f2511ee983f00163e085602','岗位删除','',1,1,'','F','system:position:remove',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c5d4096f2511ee983f00163e085602','岗位导入','',1,1,'','F','system:position:import',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('42c5d40a6f2511ee983f00163e085602','岗位导出','',1,1,'','F','system:position:export',1,'icon-default',0,'',NULL,0,0,'42c53b736f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:12',NULL,NULL,NULL,NULL,NULL,'N'),('470abfd0f31b11ef905d78fb839f9406','动态表单','dynamic',20,1,'/dynamic','M','dynamic:business:list',1,'carbon:cloud-service-management',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2025-02-25 09:53:15','188666666666','2025-02-25 09:57:24',NULL,NULL,NULL,'N'),('490e8ec16f2511ee983f00163e085602','角色组','system_role-group',60,1,'/system/role-group','C','system:roleGroup:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490eb4e76f2511ee983f00163e085602','角色组列表','',1,1,'','F','system:roleGroup:list',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490eb4e86f2511ee983f00163e085602','角色组详情','',1,1,'','F','system:roleGroup:query',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490edb0c6f2511ee983f00163e085602','角色组新增','',1,1,'','F','system:roleGroup:add',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f01326f2511ee983f00163e085602','角色组修改','',1,1,'','F','system:roleGroup:edit',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f01336f2511ee983f00163e085602','角色组删除','',1,1,'','F','system:roleGroup:remove',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f27576f2511ee983f00163e085602','角色组导入','',1,1,'','F','system:roleGroup:import',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('490f27586f2511ee983f00163e085602','角色组导出','',1,1,'','F','system:roleGroup:export',1,'icon-default',0,'',NULL,0,0,'490e8ec16f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:22',NULL,NULL,NULL,NULL,NULL,'N'),('4d61c2aac16111f18fc3a5e694323b94','脚本资产查询','dynamic_script_list',88,1,NULL,'F','dynamic:script:list',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'脚本资产列表与绑定查询',NULL,NULL,'N'),('4d61c2dcc16111f18fc3a5e694323b94','脚本资产明细','dynamic_script_query',89,1,NULL,'F','dynamic:script:query',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'资产详情/引用影响面/执行日志/版本历史',NULL,NULL,'N'),('4d61c2e6c16111f18fc3a5e694323b94','脚本资产新增','dynamic_script_add',90,1,NULL,'F','dynamic:script:add',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'新建脚本资产（沙箱校验）',NULL,NULL,'N'),('4d61c2fac16111f18fc3a5e694323b94','脚本资产编辑','dynamic_script_edit',91,1,NULL,'F','dynamic:script:edit',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'编辑资产与挂点绑定',NULL,NULL,'N'),('4d61c304c16111f18fc3a5e694323b94','脚本资产删除','dynamic_script_remove',92,1,NULL,'F','dynamic:script:remove',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'删除资产（被引用时拒绝）',NULL,NULL,'N'),('4d61c30ec16111f18fc3a5e694323b94','脚本资产发布','dynamic_script_publish',93,1,NULL,'F','dynamic:script:publish',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'发布/停用（试运行纪律）',NULL,NULL,'N'),('4d61c318c16111f18fc3a5e694323b94','脚本试运行','dynamic_script_run',94,1,NULL,'F','dynamic:script:run',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 16:38:27',NULL,NULL,'试运行与按编码直跑',NULL,NULL,'N'),('4f131aa6c17811f18fc3a5e694323b94','AI模型配置查询','system_ai_config_query',96,1,NULL,'F','system:aiConfig:query',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 19:23:09',NULL,NULL,'查看本组织模型配置（apiKey 掩码）',NULL,NULL,'N'),('4f131aecc17811f18fc3a5e694323b94','AI模型配置保存','system_ai_config_save',97,1,NULL,'F','system:aiConfig:save',1,NULL,0,NULL,NULL,NULL,NULL,'0','16663888968888889','system-default',1,'system','2026-10-06 19:23:09',NULL,NULL,'保存/启停模型配置（公网模型须明示数据流向）',NULL,NULL,'N'),('51ff13f66f2511ee983f00163e085602','角色组与角色','system_role-group-role',999,1,'/system/role-group-role','C','system:roleGroupRole:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff13f76f2511ee983f00163e085602','角色组与角色列表','',1,1,'','F','system:roleGroupRole:list',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff3a1b6f2511ee983f00163e085602','角色组与角色详情','',1,1,'','F','system:roleGroupRole:query',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff60416f2511ee983f00163e085602','角色组与角色新增','',1,1,'','F','system:roleGroupRole:add',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff86676f2511ee983f00163e085602','角色组与角色修改','',1,1,'','F','system:roleGroupRole:edit',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ff86686f2511ee983f00163e085602','角色组与角色删除','',1,1,'','F','system:roleGroupRole:remove',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ffac8c6f2511ee983f00163e085602','角色组与角色导入','',1,1,'','F','system:roleGroupRole:import',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('51ffac8d6f2511ee983f00163e085602','角色组与角色导出','',1,1,'','F','system:roleGroupRole:export',1,'icon-default',0,'',NULL,0,0,'51ff13f66f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:37',NULL,NULL,NULL,NULL,NULL,'N'),('57f757506f2511ee983f00163e085602','角色管理','system_role',100,1,'/system/role','C','system:role:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47','188666666666','2024-06-20 15:44:10',NULL,NULL,NULL,'N'),('57f77d756f2511ee983f00163e085602','角色列表','',1,1,'','F','system:role:list',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f77d766f2511ee983f00163e085602','角色详情','',1,1,'','F','system:role:query',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7a39b6f2511ee983f00163e085602','角色新增','',1,1,'','F','system:role:add',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7a39c6f2511ee983f00163e085602','角色修改','',1,1,'','F','system:role:edit',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7c9c16f2511ee983f00163e085602','角色删除','',1,1,'','F','system:role:remove',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7efe66f2511ee983f00163e085602','角色导入','',1,1,'','F','system:role:import',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('57f7efe76f2511ee983f00163e085602','角色导出','',1,1,'','F','system:role:export',1,'icon-default',0,'',NULL,0,0,'57f757506f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:47',NULL,NULL,NULL,NULL,NULL,'N'),('5dda25806f2511ee983f00163e085602','角色与菜单关系','system_role-menu',1,1,'/system/role-menu','C','system:roleMenu:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57','188666666666','2024-06-07 15:20:02',NULL,NULL,NULL,'N'),('5dda4ba56f2511ee983f00163e085602','角色与菜单关系列表','',1,1,'','F','system:roleMenu:list',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda71cb6f2511ee983f00163e085602','角色与菜单关系详情','',1,1,'','F','system:roleMenu:query',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda71cc6f2511ee983f00163e085602','角色与菜单关系新增','',1,1,'','F','system:roleMenu:add',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda97f16f2511ee983f00163e085602','角色与菜单关系修改','',1,1,'','F','system:roleMenu:edit',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5dda97f26f2511ee983f00163e085602','角色与菜单关系删除','',1,1,'','F','system:roleMenu:remove',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5ddabe166f2511ee983f00163e085602','角色与菜单关系导入','',1,1,'','F','system:roleMenu:import',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('5ddabe176f2511ee983f00163e085602','角色与菜单关系导出','',1,1,'','F','system:roleMenu:export',1,'icon-default',0,'',NULL,0,0,'5dda25806f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:47:57',NULL,NULL,NULL,NULL,NULL,'N'),('63dc8ad86f2511ee983f00163e085602','角色用户与部门关系','system_role-user-dept',1,1,'/system/role-user-dept','C','system:roleUserDept:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07','188666666666','2024-06-07 15:20:10',NULL,NULL,NULL,'N'),('63dc8ad96f2511ee983f00163e085602','角色用户与部门关系列表','',1,1,'','F','system:roleUserDept:list',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcb0fe6f2511ee983f00163e085602','角色用户与部门关系详情','',1,1,'','F','system:roleUserDept:query',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcd7236f2511ee983f00163e085602','角色用户与部门关系新增','',1,1,'','F','system:roleUserDept:add',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcd7246f2511ee983f00163e085602','角色用户与部门关系修改','',1,1,'','F','system:roleUserDept:edit',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dcfd496f2511ee983f00163e085602','角色用户与部门关系删除','',1,1,'','F','system:roleUserDept:remove',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dd236f6f2511ee983f00163e085602','角色用户与部门关系导入','',1,1,'','F','system:roleUserDept:import',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('63dd23706f2511ee983f00163e085602','角色用户与部门关系导出','',1,1,'','F','system:roleUserDept:export',1,'icon-default',0,'',NULL,0,0,'63dc8ad86f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:07',NULL,NULL,NULL,NULL,NULL,'N'),('69b5cfa06f2511ee983f00163e085602','角色与用户关系','system_role-user',999,1,'/system/role-user','C','system:roleUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b5f5c66f2511ee983f00163e085602','角色与用户关系列表','',1,1,'','F','system:roleUser:list',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b5f5c76f2511ee983f00163e085602','角色与用户关系详情','',1,1,'','F','system:roleUser:query',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b61beb6f2511ee983f00163e085602','角色与用户关系新增','',1,1,'','F','system:roleUser:add',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b61bec6f2511ee983f00163e085602','角色与用户关系修改','',1,1,'','F','system:roleUser:edit',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b642116f2511ee983f00163e085602','角色与用户关系删除','',1,1,'','F','system:roleUser:remove',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b642126f2511ee983f00163e085602','角色与用户关系导入','',1,1,'','F','system:roleUser:import',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('69b668376f2511ee983f00163e085602','角色与用户关系导出','',1,1,'','F','system:roleUser:export',1,'icon-default',0,'',NULL,0,0,'69b5cfa06f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:17',NULL,NULL,NULL,NULL,NULL,'N'),('6c1d7c80bf4411f18fc3a5e694323b94','定时任务','job',25,1,'/job','M','',1,'icon-default',0,'basic',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1dc370bf4411f18fc3a5e694323b94','任务管理','job_task',1,1,'/job/task','C','schedule:task:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1dc398bf4411f18fc3a5e694323b94','执行记录','job_log',2,1,'/job/log','C','schedule:log:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1dc3a2bf4411f18fc3a5e694323b94','实例状态','job_instance',3,1,'/job/instance','C','schedule:instance:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3512bf4411f18fc3a5e694323b94','任务查询','',1,1,'','F','schedule:task:query',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3526bf4411f18fc3a5e694323b94','任务新增','',1,1,'','F','schedule:task:add',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3530bf4411f18fc3a5e694323b94','任务编辑','',1,1,'','F','schedule:task:edit',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3544bf4411f18fc3a5e694323b94','任务删除','',1,1,'','F','schedule:task:delete',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e354ebf4411f18fc3a5e694323b94','手动执行','',1,1,'','F','schedule:task:run',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3558bf4411f18fc3a5e694323b94','终止运行','',1,1,'','F','schedule:task:terminate',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e3562bf4411f18fc3a5e694323b94','批量启停','',1,1,'','F','schedule:task:pause-batch',1,'icon-default',0,'',NULL,0,0,'6c1dc370bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6c1e356cbf4411f18fc3a5e694323b94','日志重跑','',1,1,'','F','schedule:log:retry',1,'icon-default',0,'',NULL,0,0,'6c1dc398bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 00:06:41',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a294f6f2511ee983f00163e085602','社交账号','system_social-user',180,1,'/system/social-user','C','system:socialUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a4f746f2511ee983f00163e085602','社交账号列表','',1,1,'','F','system:socialUser:list',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a759a6f2511ee983f00163e085602','社交账号详情','',1,1,'','F','system:socialUser:query',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a759b6f2511ee983f00163e085602','社交账号新增','',1,1,'','F','system:socialUser:add',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a9bc06f2511ee983f00163e085602','社交账号修改','',1,1,'','F','system:socialUser:edit',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0a9bc16f2511ee983f00163e085602','社交账号删除','',1,1,'','F','system:socialUser:remove',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0ac1e56f2511ee983f00163e085602','社交账号导入','',1,1,'','F','system:socialUser:import',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('6f0ac1e66f2511ee983f00163e085602','社交账号导出','',1,1,'','F','system:socialUser:export',1,'icon-default',0,'',NULL,0,0,'6f0a294f6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:26',NULL,NULL,NULL,NULL,NULL,'N'),('747490be6f2511ee983f00163e085602','用户兼职部门','system_user-department',999,1,'/system/user-department','C','system:userDepartment:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474b6e36f2511ee983f00163e085602','用户兼职部门列表','',1,1,'','F','system:userDepartment:list',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474b6e46f2511ee983f00163e085602','用户兼职部门详情','',1,1,'','F','system:userDepartment:query',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474dd096f2511ee983f00163e085602','用户兼职部门新增','',1,1,'','F','system:userDepartment:add',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7474dd0a6f2511ee983f00163e085602','用户兼职部门修改','',1,1,'','F','system:userDepartment:edit',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('7475032f6f2511ee983f00163e085602','用户兼职部门删除','',1,1,'','F','system:userDepartment:remove',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('747529546f2511ee983f00163e085602','用户兼职部门导入','',1,1,'','F','system:userDepartment:import',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('747529556f2511ee983f00163e085602','用户兼职部门导出','',1,1,'','F','system:userDepartment:export',1,'icon-default',0,'',NULL,0,0,'747490be6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:35',NULL,NULL,NULL,NULL,NULL,'N'),('79bae89b6f2511ee983f00163e085602','用户组','system_user-group',90,1,'/system/user-group','C','system:userGroup:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44','188666666666','2024-06-20 15:43:55',NULL,NULL,NULL,'N'),('79bb0ec16f2511ee983f00163e085602','用户组列表','',1,1,'','F','system:userGroup:list',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb0ec26f2511ee983f00163e085602','用户组详情','',1,1,'','F','system:userGroup:query',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb34e76f2511ee983f00163e085602','用户组新增','',1,1,'','F','system:userGroup:add',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb5b0c6f2511ee983f00163e085602','用户组修改','',1,1,'','F','system:userGroup:edit',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb5b0d6f2511ee983f00163e085602','用户组删除','',1,1,'','F','system:userGroup:remove',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb81326f2511ee983f00163e085602','用户组导入','',1,1,'','F','system:userGroup:import',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('79bb81336f2511ee983f00163e085602','用户组导出','',1,1,'','F','system:userGroup:export',1,'icon-default',0,'',NULL,0,0,'79bae89b6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:44',NULL,NULL,NULL,NULL,NULL,'N'),('7ede62156f2511ee983f00163e085602','用户组与用户','system_user-group-user',999,1,'/system/user-group-user','C','system:userGroupUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7ede62166f2511ee983f00163e085602','用户组与用户列表','',1,1,'','F','system:userGroupUser:list',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7ede883b6f2511ee983f00163e085602','用户组与用户详情','',1,1,'','F','system:userGroupUser:query',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7ede883c6f2511ee983f00163e085602','用户组与用户新增','',1,1,'','F','system:userGroupUser:add',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7edeae606f2511ee983f00163e085602','用户组与用户修改','',1,1,'','F','system:userGroupUser:edit',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7edeae616f2511ee983f00163e085602','用户组与用户删除','',1,1,'','F','system:userGroupUser:remove',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7eded4866f2511ee983f00163e085602','用户组与用户导入','',1,1,'','F','system:userGroupUser:import',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('7edefaab6f2511ee983f00163e085602','用户组与用户导出','',1,1,'','F','system:userGroupUser:export',1,'icon-default',0,'',NULL,0,0,'7ede62156f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:48:52',NULL,NULL,NULL,NULL,NULL,'N'),('85a389856f2511ee983f00163e085602','用户管理','system_user',110,1,'/system/user','C','system:user:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04','188666666666','2024-06-20 15:42:44',NULL,NULL,NULL,'N'),('85a389866f2511ee983f00163e085602','用户列表','',1,1,'','F','system:user:list',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3afab6f2511ee983f00163e085602','用户详情','',1,1,'','F','system:user:query',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3d5d16f2511ee983f00163e085602','用户新增','',1,1,'','F','system:user:add',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3d5d26f2511ee983f00163e085602','用户修改','',1,1,'','F','system:user:edit',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a3fbf66f2511ee983f00163e085602','用户删除','',1,1,'','F','system:user:remove',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a4221c6f2511ee983f00163e085602','用户导入','',1,1,'','F','system:user:import',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('85a4221d6f2511ee983f00163e085602','用户导出','',1,1,'','F','system:user:export',1,'icon-default',0,'',NULL,0,0,'85a389856f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:04',NULL,NULL,NULL,NULL,NULL,'N'),('888f1f526b0b11ed9d555855a3323333','我发起的','task_instance',4,1,'/task/instance','C','wfs:processInstance:list',1,'icon-default',0,'self',NULL,0,0,'88ff1f526b0b11ed9d555855a3321111','16663888968888889','system-default',1,'188666666666','2022-11-14 10:27:21',NULL,NULL,NULL,NULL,NULL,'N'),('88ff1f526b0b11ed9d555855a3321111','我的流程','task',30,1,'/task','M','wfs:task:list',1,'mdi:monitor-dashboard',0,'basic',NULL,0,0,'0','16663888968888889','system-default',1,'188666666666','2022-11-14 10:24:11',NULL,NULL,NULL,NULL,NULL,'N'),('88ff1f526b0b11ed9d555855a3322222','待办','task_todo',1,1,'/task/todo','C','wfs:task:list',1,'icon-default',0,'self',NULL,0,0,'88ff1f526b0b11ed9d555855a3321111','16663888968888889','system-default',1,'188666666666','2022-11-14 10:25:55',NULL,NULL,NULL,NULL,NULL,'N'),('88ff1f526b0b11ed9d555855a3323333','已办','task_done',2,1,'/task/done','C','wfs:task:list',1,'icon-default',0,'self',NULL,0,0,'88ff1f526b0b11ed9d555855a3321111','16663888968888889','system-default',1,'188666666666','2022-11-14 10:27:21',NULL,NULL,NULL,NULL,NULL,'N'),('8ad066416f2511ee983f00163e085602','用户的岗位','system_user-position',999,1,'/system/user-position','C','system:userPosition:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad08c676f2511ee983f00163e085602','用户的岗位列表','',1,1,'','F','system:userPosition:list',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad08c686f2511ee983f00163e085602','用户的岗位详情','',1,1,'','F','system:userPosition:query',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0b28c6f2511ee983f00163e085602','用户的岗位新增','',1,1,'','F','system:userPosition:add',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0b28d6f2511ee983f00163e085602','用户的岗位修改','',1,1,'','F','system:userPosition:edit',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0d8b26f2511ee983f00163e085602','用户的岗位删除','',1,1,'','F','system:userPosition:remove',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad0fed86f2511ee983f00163e085602','用户的岗位导入','',1,1,'','F','system:userPosition:import',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('8ad124fd6f2511ee983f00163e085602','用户的岗位导出','',1,1,'','F','system:userPosition:export',1,'icon-default',0,'',NULL,0,0,'8ad066416f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:12',NULL,NULL,NULL,NULL,NULL,'N'),('91f5a3f8bfe211f18fc3a5e694323b94','任务报警列表','job_alarm',4,1,'/job/alarm','C','schedule:alarm:list',1,'icon-default',0,'self',NULL,0,0,'6c1d7c80bf4411f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-04 18:58:45',NULL,NULL,NULL,NULL,NULL,'N'),('94eaa51d6f2511ee983f00163e085602','用户服务组织','system_user-service-org',999,1,'/system/user-service-org','C','system:userServiceOrg:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eaa51e6f2511ee983f00163e085602','用户服务组织列表','',1,1,'','F','system:userServiceOrg:list',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eacb426f2511ee983f00163e085602','用户服务组织详情','',1,1,'','F','system:userServiceOrg:query',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eaf1686f2511ee983f00163e085602','用户服务组织新增','',1,1,'','F','system:userServiceOrg:add',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eaf1696f2511ee983f00163e085602','用户服务组织修改','',1,1,'','F','system:userServiceOrg:edit',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eb178e6f2511ee983f00163e085602','用户服务组织删除','',1,1,'','F','system:userServiceOrg:remove',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eb178f6f2511ee983f00163e085602','用户服务组织导入','',1,1,'','F','system:userServiceOrg:import',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('94eb3db36f2511ee983f00163e085602','用户服务组织导出','',1,1,'','F','system:userServiceOrg:export',1,'icon-default',0,'',NULL,0,0,'94eaa51d6f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:29',NULL,NULL,NULL,NULL,NULL,'N'),('9a36d84cf31b11ef905d78fb839f9406','动态数据库','dynamic_database',1,1,'/dynamic/database','C','dynamic:database:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a372feaf31b11ef905d78fb839f9406','动态数据库列表','',6,1,'','F','dynamic:database:list',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a373c88f31b11ef905d78fb839f9406','动态数据库详情','',1,1,'','F','dynamic:database:query',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a374b88f31b11ef905d78fb839f9406','动态数据库新增','',5,1,'','F','dynamic:database:add',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a3757ccf31b11ef905d78fb839f9406','动态数据库修改','',4,1,'','F','dynamic:database:edit',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a3765d2f31b11ef905d78fb839f9406','动态数据库删除','',3,1,'','F','dynamic:database:remove',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a3776eef31b11ef905d78fb839f9406','动态数据库导入','',2,1,'','F','dynamic:database:import',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a378382f31b11ef905d78fb839f9406','动态数据库导出','',1,1,'','F','dynamic:database:export',1,'icon-default',0,'',NULL,0,0,'9a36d84cf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:34',NULL,NULL,NULL,NULL,NULL,'N'),('9a71582cdf9e11ee9b3d00163e085602','系统登录日志','message_system-log-login',160,1,'/message/system-log-login','C','message:systemLogLogin:list',1,'icon-default',0,'self',NULL,0,0,'1814169974018928641','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58','188666666666','2024-07-19 13:27:31',NULL,NULL,NULL,'N'),('9a717e52df9e11ee9b3d00163e085602','系统登录日志列表','',1,1,'','F','message:systemLogLogin:list',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a717e53df9e11ee9b3d00163e085602','系统登录日志详情','',1,1,'','F','message:systemLogLogin:query',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71a478df9e11ee9b3d00163e085602','系统登录日志新增','',1,1,'','F','message:systemLogLogin:add',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71a479df9e11ee9b3d00163e085602','系统登录日志修改','',1,1,'','F','message:systemLogLogin:edit',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71ca9edf9e11ee9b3d00163e085602','系统登录日志删除','',1,1,'','F','message:systemLogLogin:remove',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71f0c4df9e11ee9b3d00163e085602','系统登录日志导入','',1,1,'','F','message:systemLogLogin:import',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9a71f0c5df9e11ee9b3d00163e085602','系统登录日志导出','',1,1,'','F','message:systemLogLogin:export',1,'icon-default',0,'',NULL,0,0,'9a71582cdf9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:57:58',NULL,NULL,NULL,NULL,NULL,'N'),('9b9461826f2511ee983f00163e085602','社交帐号关联系统账号','system_user-social-user',999,1,'/system/user-social-user','C','system:userSocialUser:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:40',NULL,NULL,NULL,NULL,NULL,'N'),('9b9461836f2511ee983f00163e085602','社交帐号关联系统账号列表','',1,1,'','F','system:userSocialUser:list',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:40',NULL,NULL,NULL,NULL,NULL,'N'),('9b9487a86f2511ee983f00163e085602','社交帐号关联系统账号详情','',1,1,'','F','system:userSocialUser:query',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:40',NULL,NULL,NULL,NULL,NULL,'N'),('9b94adcd6f2511ee983f00163e085602','社交帐号关联系统账号新增','',1,1,'','F','system:userSocialUser:add',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94adce6f2511ee983f00163e085602','社交帐号关联系统账号修改','',1,1,'','F','system:userSocialUser:edit',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94d3f36f2511ee983f00163e085602','社交帐号关联系统账号删除','',1,1,'','F','system:userSocialUser:remove',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94d3f46f2511ee983f00163e085602','社交帐号关联系统账号导入','',1,1,'','F','system:userSocialUser:import',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('9b94fa196f2511ee983f00163e085602','社交帐号关联系统账号导出','',1,1,'','F','system:userSocialUser:export',1,'icon-default',0,'',NULL,0,0,'9b9461826f2511ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:49:41',NULL,NULL,NULL,NULL,NULL,'N'),('a0ba9526df9e11ee9b3d00163e085602','系统操作日志','message_system-log-operation',150,1,'/message/system-log-operation','C','message:systemLogOperation:list',1,'icon-default',0,'self',NULL,0,0,'1814169974018928641','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08','188666666666','2024-07-19 13:27:42',NULL,NULL,NULL,'N'),('a0ba9527df9e11ee9b3d00163e085602','系统操作日志列表','',1,1,'','F','message:systemLogOperation:list',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0babb4cdf9e11ee9b3d00163e085602','系统操作日志详情','',1,1,'','F','message:systemLogOperation:query',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bae172df9e11ee9b3d00163e085602','系统操作日志新增','',1,1,'','F','message:systemLogOperation:add',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bae173df9e11ee9b3d00163e085602','系统操作日志修改','',1,1,'','F','message:systemLogOperation:edit',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bb0798df9e11ee9b3d00163e085602','系统操作日志删除','',1,1,'','F','message:systemLogOperation:remove',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bb0799df9e11ee9b3d00163e085602','系统操作日志导入','',1,1,'','F','message:systemLogOperation:import',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a0bb2dbedf9e11ee9b3d00163e085602','系统操作日志导出','',1,1,'','F','message:systemLogOperation:export',1,'icon-default',0,'',NULL,0,0,'a0ba9526df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:08',NULL,NULL,NULL,NULL,NULL,'N'),('a2ba9abcf31b11ef905d78fb839f9406','表单的列','dynamic_form-column',1,1,'/dynamic/form-column','C','dynamic:formColumn:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49','188666666666','2025-02-25 09:58:08',NULL,NULL,NULL,'N'),('a2baac46f31b11ef905d78fb839f9406','表单的列的数据列表','',6,1,'','F','dynamic:formColumn:list',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bab722f31b11ef905d78fb839f9406','表单的列的数据详情','',1,1,'','F','dynamic:formColumn:query',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bac122f31b11ef905d78fb839f9406','表单的列的数据新增','',5,1,'','F','dynamic:formColumn:add',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bacd7af31b11ef905d78fb839f9406','表单的列的数据修改','',4,1,'','F','dynamic:formColumn:edit',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bad770f31b11ef905d78fb839f9406','表单的列的数据删除','',3,1,'','F','dynamic:formColumn:remove',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2bae116f31b11ef905d78fb839f9406','表单的列的数据导入','',2,1,'','F','dynamic:formColumn:import',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a2baea9ef31b11ef905d78fb839f9406','表单的列的数据导出','',1,1,'','F','dynamic:formColumn:export',1,'icon-default',0,'',NULL,0,0,'a2ba9abcf31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:55:49',NULL,NULL,NULL,NULL,NULL,'N'),('a64beef4df9e11ee9b3d00163e085602','系统消息','message_system-message',120,1,'/message/system-message','C','message:systemMessage:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18','188666666666','2024-06-07 15:23:07',NULL,NULL,NULL,'N'),('a64beef5df9e11ee9b3d00163e085602','系统消息列表','',1,1,'','F','message:systemMessage:list',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c151adf9e11ee9b3d00163e085602','系统消息详情','',1,1,'','F','message:systemMessage:query',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c151bdf9e11ee9b3d00163e085602','系统消息新增','',1,1,'','F','message:systemMessage:add',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c878cdf9e11ee9b3d00163e085602','系统消息修改','',1,1,'','F','message:systemMessage:edit',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64c878ddf9e11ee9b3d00163e085602','系统消息删除','',1,1,'','F','message:systemMessage:remove',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64cadb2df9e11ee9b3d00163e085602','系统消息导入','',1,1,'','F','message:systemMessage:import',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('a64cadb3df9e11ee9b3d00163e085602','系统消息导出','',1,1,'','F','message:systemMessage:export',1,'icon-default',0,'',NULL,0,0,'a64beef4df9e11ee9b3d00163e085602','16663888968888889','system-default',1,'188666666666','2024-03-11 19:58:18',NULL,NULL,NULL,NULL,NULL,'N'),('ac47fd90f31b11ef905d78fb839f9406','表单的历史版本','dynamic_form-log',1,1,'/dynamic/form-log','C','dynamic:formLog:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05','188666666666','2025-02-25 09:58:22',NULL,NULL,NULL,'N'),('ac481618f31b11ef905d78fb839f9406','表单的历史版本列表','',6,1,'','F','dynamic:formLog:list',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac482248f31b11ef905d78fb839f9406','表单的历史版本详情','',1,1,'','F','dynamic:formLog:query',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac482d7ef31b11ef905d78fb839f9406','表单的历史版本新增','',5,1,'','F','dynamic:formLog:add',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac483846f31b11ef905d78fb839f9406','表单的历史版本修改','',4,1,'','F','dynamic:formLog:edit',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac484340f31b11ef905d78fb839f9406','表单的历史版本删除','',3,1,'','F','dynamic:formLog:remove',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac486618f31b11ef905d78fb839f9406','表单的历史版本导入','',2,1,'','F','dynamic:formLog:import',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('ac48752cf31b11ef905d78fb839f9406','表单的历史版本导出','',1,1,'','F','dynamic:formLog:export',1,'icon-default',0,'',NULL,0,0,'ac47fd90f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:05',NULL,NULL,NULL,NULL,NULL,'N'),('b1e4ac20f31b11ef905d78fbdead0001','报表设计器','report-designer',15,1,'/report-designer','C','report:dataset:add',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2026-10-02 23:15:46',NULL,NULL,NULL,NULL,NULL,'N'),('b1efac20f31b11ef905d78fb839f9406','表单','dynamic_form',1,1,'/dynamic/form','C','dynamic:form:list',1,'icon-default',0,'self',NULL,0,0,'470abfd0f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14','188666666666','2025-02-25 09:58:35',NULL,NULL,NULL,'N'),('b1efc02af31b11ef905d78fb839f9406','表单列表','',6,1,'','F','dynamic:form:list',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1efcf52f31b11ef905d78fb839f9406','表单详情','',1,1,'','F','dynamic:form:query',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1efe41af31b11ef905d78fb839f9406','表单新增','',5,1,'','F','dynamic:form:add',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1eff57cf31b11ef905d78fb839f9406','表单修改','',4,1,'','F','dynamic:form:edit',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1f006d4f31b11ef905d78fb839f9406','表单删除','',3,1,'','F','dynamic:form:remove',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1f01e3af31b11ef905d78fb839f9406','表单导入','',2,1,'','F','dynamic:form:import',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('b1f0338ef31b11ef905d78fb839f9406','表单导出','',1,1,'','F','dynamic:form:export',1,'icon-default',0,'',NULL,0,0,'b1efac20f31b11ef905d78fb839f9406','16663888968888889','system-default',1,'188666666666','2025-02-25 09:56:14',NULL,NULL,NULL,NULL,NULL,'N'),('bfa051ec6f2411ee983f00163e085602','第三方应用配置(小程序)','system_app-config',100,1,'/system/app-config','C','system:appConfig:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa078126f2411ee983f00163e085602','第三方应用配置(小程序)列表','',1,1,'','F','system:appConfig:list',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa078136f2411ee983f00163e085602','第三方应用配置(小程序)详情','',1,1,'','F','system:appConfig:query',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa09e376f2411ee983f00163e085602','第三方应用配置(小程序)新增','',1,1,'','F','system:appConfig:add',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa09e386f2411ee983f00163e085602','第三方应用配置(小程序)修改','',1,1,'','F','system:appConfig:edit',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa0c45d6f2411ee983f00163e085602','第三方应用配置(小程序)删除','',1,1,'','F','system:appConfig:remove',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:31',NULL,NULL,NULL,NULL,NULL,'N'),('bfa0ea826f2411ee983f00163e085602','第三方应用配置(小程序)导入','',1,1,'','F','system:appConfig:import',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:32',NULL,NULL,NULL,NULL,NULL,'N'),('bfa0ea836f2411ee983f00163e085602','第三方应用配置(小程序)导出','',1,1,'','F','system:appConfig:export',1,'icon-default',0,'',NULL,0,0,'bfa051ec6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:32',NULL,NULL,NULL,NULL,NULL,'N'),('c55cb4616f1911ee983f00163e085602','文件信息','system_file-info',90,1,'/system/file-info','C','system:fileInfo:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55cda866f1911ee983f00163e085602','文件信息列表','',1,1,'','F','system:fileInfo:list',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55cda876f1911ee983f00163e085602','文件信息详情','',1,1,'','F','system:fileInfo:query',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d00ac6f1911ee983f00163e085602','文件信息新增','',1,1,'','F','system:fileInfo:add',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d00ad6f1911ee983f00163e085602','文件信息修改','',1,1,'','F','system:fileInfo:edit',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d26d26f1911ee983f00163e085602','文件信息删除','',1,1,'','F','system:fileInfo:remove',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d26d36f1911ee983f00163e085602','文件信息导入','',1,1,'','F','system:fileInfo:import',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('c55d4cf76f1911ee983f00163e085602','文件信息导出','',1,1,'','F','system:fileInfo:export',1,'icon-default',0,'',NULL,0,0,'c55cb4616f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:24:57',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e0e2e6f2411ee983f00163e085602','应用管理','system_app-info',170,1,'/system/app-info','C','system:appInfo:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54','188666666666','2024-06-07 15:20:53',NULL,NULL,NULL,'N'),('cd2e34546f2411ee983f00163e085602','应用列表','',1,1,'','F','system:appInfo:list',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e34556f2411ee983f00163e085602','应用详情','',1,1,'','F','system:appInfo:query',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e5a796f2411ee983f00163e085602','应用新增','',1,1,'','F','system:appInfo:add',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e5a7a6f2411ee983f00163e085602','应用修改','',1,1,'','F','system:appInfo:edit',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e809f6f2411ee983f00163e085602','应用删除','',1,1,'','F','system:appInfo:remove',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2e80a06f2411ee983f00163e085602','应用导入','',1,1,'','F','system:appInfo:import',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('cd2ea6c56f2411ee983f00163e085602','应用导出','',1,1,'','F','system:appInfo:export',1,'icon-default',0,'',NULL,0,0,'cd2e0e2e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:43:54',NULL,NULL,NULL,NULL,NULL,'N'),('d17ed0c8befa11f18fc3a5e694323b94','OpenAPI凭证','system_openapi-app',190,1,'/system/openapi-app','C','system:openapi:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'开放与集成-OpenAPI应用凭证与调用统计',NULL,NULL,'N'),('d17ed0f0befa11f18fc3a5e694323b94','Webhook订阅','system_webhook',191,1,'/system/webhook','C','system:webhook:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'开放与集成-Webhook事件订阅与投递记录',NULL,NULL,'N'),('d17ed0fabefa11f18fc3a5e694323b94','单点登录配置','system_sso-config',192,1,'/system/sso-config','C','system:ssoConfig:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'开放与集成-SSO OIDC渠道配置',NULL,NULL,'N'),('d17f3b94befa11f18fc3a5e694323b94','凭证详情','',1,1,'','F','system:openapi:query',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-凭证详情',NULL,NULL,'N'),('d17f3bb2befa11f18fc3a5e694323b94','凭证新增','',1,1,'','F','system:openapi:add',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-新建凭证（明文Secret仅此一次返回）',NULL,NULL,'N'),('d17f3bc6befa11f18fc3a5e694323b94','凭证修改','',1,1,'','F','system:openapi:edit',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-改名称/范围/授权表/状态',NULL,NULL,'N'),('d17f3bd0befa11f18fc3a5e694323b94','凭证删除','',1,1,'','F','system:openapi:remove',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-删除凭证',NULL,NULL,'N'),('d17f3bdabefa11f18fc3a5e694323b94','凭证轮换','',1,1,'','F','system:openapi:rotate',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-轮换Secret',NULL,NULL,'N'),('d17f3be4befa11f18fc3a5e694323b94','调用统计','',1,1,'','F','system:openapi:callLog',1,'icon-default',0,'',NULL,0,0,'d17ed0c8befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'OpenAPI-查看调用流水',NULL,NULL,'N'),('d17f3beebefa11f18fc3a5e694323b94','订阅详情','',1,1,'','F','system:webhook:query',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-订阅详情',NULL,NULL,'N'),('d17f3bf8befa11f18fc3a5e694323b94','订阅新增','',1,1,'','F','system:webhook:add',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-新建订阅',NULL,NULL,'N'),('d17f3c02befa11f18fc3a5e694323b94','订阅修改','',1,1,'','F','system:webhook:edit',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-修改订阅',NULL,NULL,'N'),('d17f3c0cbefa11f18fc3a5e694323b94','订阅删除','',1,1,'','F','system:webhook:remove',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-删除订阅',NULL,NULL,'N'),('d17f3c16befa11f18fc3a5e694323b94','事件重发','',1,1,'','F','system:webhook:resend',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-手动重发事件',NULL,NULL,'N'),('d17f3c20befa11f18fc3a5e694323b94','投递记录','',1,1,'','F','system:webhook:delivery',1,'icon-default',0,'',NULL,0,0,'d17ed0f0befa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'Webhook-查看投递记录',NULL,NULL,'N'),('d17f3c2abefa11f18fc3a5e694323b94','渠道详情','',1,1,'','F','system:ssoConfig:query',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-渠道详情',NULL,NULL,'N'),('d17f3c34befa11f18fc3a5e694323b94','渠道新增','',1,1,'','F','system:ssoConfig:add',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-新建OIDC渠道',NULL,NULL,'N'),('d17f3c3ebefa11f18fc3a5e694323b94','渠道修改','',1,1,'','F','system:ssoConfig:edit',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-修改OIDC渠道',NULL,NULL,'N'),('d17f3c48befa11f18fc3a5e694323b94','渠道删除','',1,1,'','F','system:ssoConfig:remove',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-删除OIDC渠道',NULL,NULL,'N'),('d17f3c52befa11f18fc3a5e694323b94','渠道测试','',1,1,'','F','system:ssoConfig:test',1,'icon-default',0,'',NULL,0,0,'d17ed0fabefa11f18fc3a5e694323b94','16663888968888889','system-default',1,'188666666666','2026-10-03 15:19:49',NULL,NULL,'SSO-发现文档连通性测试',NULL,NULL,'N'),('d399cd6f6f2411ee983f00163e085602','应用的接口','system_app-path',999,1,'/system/app-path','C','system:appPath:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d399f3956f2411ee983f00163e085602','应用的接口列表','',1,1,'','F','system:appPath:list',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a19bb6f2411ee983f00163e085602','应用的接口详情','',1,1,'','F','system:appPath:query',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a19bc6f2411ee983f00163e085602','应用的接口新增','',1,1,'','F','system:appPath:add',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a3fe06f2411ee983f00163e085602','应用的接口修改','',1,1,'','F','system:appPath:edit',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a3fe16f2411ee983f00163e085602','应用的接口删除','',1,1,'','F','system:appPath:remove',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a66066f2411ee983f00163e085602','应用的接口导入','',1,1,'','F','system:appPath:import',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d39a66076f2411ee983f00163e085602','应用的接口导出','',1,1,'','F','system:appPath:export',1,'icon-default',0,'',NULL,0,0,'d399cd6f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:05',NULL,NULL,NULL,NULL,NULL,'N'),('d46cd3bd6f1911ee983f00163e085602','文件配置','system_file-config',80,1,'/system/file-config','C','system:fileConfig:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22','188666666666','2024-06-07 15:54:15',NULL,NULL,NULL,'N'),('d46cf9e26f1911ee983f00163e085602','文件配置列表','',1,1,'','F','system:fileConfig:list',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d20086f1911ee983f00163e085602','文件配置详情','',1,1,'','F','system:fileConfig:query',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d20096f1911ee983f00163e085602','文件配置新增','',1,1,'','F','system:fileConfig:add',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d462d6f1911ee983f00163e085602','文件配置修改','',1,1,'','F','system:fileConfig:edit',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d462e6f1911ee983f00163e085602','文件配置删除','',1,1,'','F','system:fileConfig:remove',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d6c536f1911ee983f00163e085602','文件配置导入','',1,1,'','F','system:fileConfig:import',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('d46d92796f1911ee983f00163e085602','文件配置导出','',1,1,'','F','system:fileConfig:export',1,'icon-default',0,'',NULL,0,0,'d46cd3bd6f1911ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 15:25:22',NULL,NULL,NULL,NULL,NULL,'N'),('da0815306f2411ee983f00163e085602','系统配置','system_default-config',110,1,'/system/default-config','C','system:defaultConfig:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16','188666666666','2024-06-07 15:54:03',NULL,NULL,NULL,'N'),('da083b566f2411ee983f00163e085602','系统配置列表','',1,1,'','F','system:defaultConfig:list',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da083b576f2411ee983f00163e085602','系统配置详情','',1,1,'','F','system:defaultConfig:query',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da08617c6f2411ee983f00163e085602','系统配置新增','',1,1,'','F','system:defaultConfig:add',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da08617d6f2411ee983f00163e085602','系统配置修改','',1,1,'','F','system:defaultConfig:edit',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da0887a16f2411ee983f00163e085602','系统配置删除','',1,1,'','F','system:defaultConfig:remove',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da0887a26f2411ee983f00163e085602','系统配置导入','',1,1,'','F','system:defaultConfig:import',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('da08adc76f2411ee983f00163e085602','系统配置导出','',1,1,'','F','system:defaultConfig:export',1,'icon-default',0,'',NULL,0,0,'da0815306f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:16',NULL,NULL,NULL,NULL,NULL,'N'),('e198a33f6f2411ee983f00163e085602','部门管理','system_department',120,1,'/system/department','C','system:department:list',1,'icon-default',0,'self',NULL,0,0,'1803694748176957442','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:28','188666666666','2024-06-20 15:43:11',NULL,NULL,NULL,'N'),('e198a3406f2411ee983f00163e085602','组织架构列表','',1,1,'','F','system:department:list',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:28',NULL,NULL,NULL,NULL,NULL,'N'),('e198c9656f2411ee983f00163e085602','组织架构详情','',1,1,'','F','system:department:query',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e198c9666f2411ee983f00163e085602','组织架构新增','',1,1,'','F','system:department:add',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e198ef8a6f2411ee983f00163e085602','组织架构修改','',1,1,'','F','system:department:edit',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e198ef8b6f2411ee983f00163e085602','组织架构删除','',1,1,'','F','system:department:remove',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e19915b06f2411ee983f00163e085602','组织架构导入','',1,1,'','F','system:department:import',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('e1993bd56f2411ee983f00163e085602','组织架构导出','',1,1,'','F','system:department:export',1,'icon-default',0,'',NULL,0,0,'e198a33f6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:29',NULL,NULL,NULL,NULL,NULL,'N'),('ee25c66b6f2411ee983f00163e085602','部门内联关系','system_dept-relation',999,1,'/system/dept-relation','C','system:deptRelation:list',1,'icon-default',0,'self',NULL,0,0,'1663888968294588891','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee25ec906f2411ee983f00163e085602','部门内联关系列表','',1,1,'','F','system:deptRelation:list',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2612b66f2411ee983f00163e085602','部门内联关系详情','',1,1,'','F','system:deptRelation:query',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2612b76f2411ee983f00163e085602','部门内联关系新增','',1,1,'','F','system:deptRelation:add',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2638dc6f2411ee983f00163e085602','部门内联关系修改','',1,1,'','F','system:deptRelation:edit',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2638dd6f2411ee983f00163e085602','部门内联关系删除','',1,1,'','F','system:deptRelation:remove',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee265f016f2411ee983f00163e085602','部门内联关系导入','',1,1,'','F','system:deptRelation:import',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('ee2685276f2411ee983f00163e085602','部门内联关系导出','',1,1,'','F','system:deptRelation:export',1,'icon-default',0,'',NULL,0,0,'ee25c66b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:44:50',NULL,NULL,NULL,NULL,NULL,'N'),('f959a70e6f2411ee983f00163e085602','字典数据','system_dictionary-data',120,0,'/system/dictionary-data','C','system:dictionaryData:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08','188666666666','2024-06-21 11:40:27',NULL,NULL,NULL,'N'),('f959cd336f2411ee983f00163e085602','字典数据列表','',1,1,'','F','system:dictionaryData:list',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f959cd346f2411ee983f00163e085602','字典数据详情','',1,1,'','F','system:dictionaryData:query',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f959f3596f2411ee983f00163e085602','字典数据新增','',1,1,'','F','system:dictionaryData:add',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a197e6f2411ee983f00163e085602','字典数据修改','',1,1,'','F','system:dictionaryData:edit',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a3fa46f2411ee983f00163e085602','字典数据删除','',1,1,'','F','system:dictionaryData:remove',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a65ca6f2411ee983f00163e085602','字典数据导入','',1,1,'','F','system:dictionaryData:import',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('f95a8bef6f2411ee983f00163e085602','字典数据导出','',1,1,'','F','system:dictionaryData:export',1,'icon-default',0,'',NULL,0,0,'f959a70e6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:08',NULL,NULL,NULL,NULL,NULL,'N'),('fe59371b6f2411ee983f00163e085602','字典类型','system_dictionary-type',118,1,'/system/dictionary-type','C','system:dictionaryType:list',1,'icon-default',0,'self',NULL,0,0,'1798986040628969473','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17','188666666666','2024-06-21 11:39:53',NULL,NULL,NULL,'N'),('fe595d416f2411ee983f00163e085602','字典类型列表','',1,1,'','F','system:dictionaryType:list',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe5983676f2411ee983f00163e085602','字典类型详情','',1,1,'','F','system:dictionaryType:query',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe5983686f2411ee983f00163e085602','字典类型新增','',1,1,'','F','system:dictionaryType:add',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59a98c6f2411ee983f00163e085602','字典类型修改','',1,1,'','F','system:dictionaryType:edit',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59a98d6f2411ee983f00163e085602','字典类型删除','',1,1,'','F','system:dictionaryType:remove',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59cfb26f2411ee983f00163e085602','字典类型导入','',1,1,'','F','system:dictionaryType:import',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N'),('fe59f5d86f2411ee983f00163e085602','字典类型导出','',1,1,'','F','system:dictionaryType:export',1,'icon-default',0,'',NULL,0,0,'fe59371b6f2411ee983f00163e085602','16663888968888889','system-default',1,'188666666666','2023-10-20 16:45:17',NULL,NULL,NULL,NULL,NULL,'N');
INSERT INTO `system_app_menu` VALUES ('1798606500543033345','1768482874137485238','304d18026f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606500626919425','1768482874137485238','26bb3ab0dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606500694028289','1768482874137485238','d46cd3bd6f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606500756942850','1768482874137485238','da0815306f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606501725827074','1768482874137485238','12fe5bef6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606501797130242','1768482874137485238','a64beef4df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798606503038644225','1768482874137485238','cd2e0e2e6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-06 14:43:01',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798980337281499137','1768482874137485248','e198a33f6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-07 15:28:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798980337386356738','1768482874137485248','57f757506f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-07 15:28:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798980337482825729','1768482874137485248','85a389856f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-07 15:28:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1798987186722537474','1768482874137485238','1798986040628969473',1,NULL,'system-default',1,'188666666666','2024-06-07 15:55:44',NULL,NULL,'N',NULL,NULL,'16663888968888889',NULL,1),('1803696170603855873','1768482874137485248','1803694748176957442',1,NULL,'system-default',1,'188666666666','2024-06-20 15:47:33',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997413599539201','1768482874137485238','f959a70e6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35','188666666666','2024-06-21 11:40:27','N',NULL,NULL,'16663888968888889',0,1),('1803997413956055042','1768482874137485238','fe59371b6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35','188666666666','2024-06-21 11:39:53','N',NULL,NULL,'16663888968888889',0,1),('1803997414669086721','1768482874137485238','f959cd336f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415029796866','1768482874137485238','f959cd346f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415222734849','1768482874137485238','f959f3596f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415482781698','1768482874137485238','f95a197e6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415654748161','1768482874137485238','f95a3fa46f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415776382978','1768482874137485238','f95a65ca6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997415939960834','1768482874137485238','f95a8bef6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416116121602','1768482874137485238','fe595d416f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416241950721','1768482874137485238','fe5983676f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416355196929','1768482874137485238','fe5983686f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997416992731137','1768482874137485238','fe59a98c6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997417177280513','1768482874137485238','fe59a98d6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997417382801410','1768482874137485238','fe59cfb26f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997417542184962','1768482874137485238','fe59f5d86f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997418796281858','1768482874137485238','d46d92796f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419043745793','1768482874137485238','d46d6c536f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419169574913','1768482874137485238','d46d462e6f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419320569857','1768482874137485238','d46d462d6f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419597393921','1768482874137485238','d46d20096f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419677085697','1768482874137485238','d46d20086f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997419995852801','1768482874137485238','d46cf9e26f1911ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420138459137','1768482874137485238','da083b566f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420306231298','1768482874137485238','da083b576f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420494974977','1768482874137485238','da08617c6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420591443970','1768482874137485238','da08617d6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997420734050306','1768482874137485238','da0887a16f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997421656797186','1768482874137485238','da0887a26f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997421958787073','1768482874137485238','da08adc76f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422080421889','1768482874137485238','26bb86fcdad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422239805441','1768482874137485238','26bbad22dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422462103553','1768482874137485238','26bbd348dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422659235841','1768482874137485238','26bbf96edad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422776676353','1768482874137485238','26bbf96fdad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997422910894082','1768482874137485238','26bc1f94dad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1803997423124803585','1768482874137485238','26bc45badad111ee9b3d00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 11:44:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001669706608641','1768482874137485248','85a389866f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:29',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001671304638465','1768482874137485248','85a3afab6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:30',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001672797810689','1768482874137485248','85a3d5d16f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:30',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001674299371522','1768482874137485248','85a3d5d26f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001675423444995','1768482874137485248','85a3fbf66f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001677587705857','1768482874137485248','85a4221c6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:31',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001678678224897','1768482874137485248','85a4221d6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:32',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001680062345217','1768482874137485248','e198a3406f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:32',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001681228361730','1768482874137485248','e198c9656f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:32',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001682578927618','1768482874137485248','e198c9666f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:33',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001686529961986','1768482874137485248','e198ef8a6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:33',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001688480313346','1768482874137485248','e198ef8b6f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:34',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001689948319745','1768482874137485248','e19915b06f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:34',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001691139502081','1768482874137485248','e1993bd56f2411ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001693232459778','1768482874137485248','57f77d756f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001694302007297','1768482874137485248','57f77d766f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:35',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001695799373825','1768482874137485248','57f7a39b6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001696952807426','1768482874137485248','57f7a39c6f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001698664083457','1768482874137485248','57f7c9c16f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:36',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001703059714049','1768482874137485248','57f7efe66f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:37',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1804001704762601474','1768482874137485248','57f7efe76f2511ee983f00163e085602',1,NULL,'system-default',1,'188666666666','2024-06-21 12:01:38',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724006428673','1768482874137485238','470abfd0f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724081926145','1768482874137485238','9a36d84cf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724149035009','1768482874137485238','9a372feaf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724199366658','1768482874137485238','9a374b88f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724253892609','1768482874137485238','9a3757ccf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724308418562','1768482874137485238','9a3765d2f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724350361601','1768482874137485238','9a3776eef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724413276161','1768482874137485238','9a373c88f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724463607810','1768482874137485238','9a378382f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724509745154','1768482874137485238','a2ba9abcf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724560076801','1768482874137485238','a2baac46f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724606214146','1768482874137485238','a2bac122f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724656545793','1768482874137485238','a2bacd7af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724706877441','1768482874137485238','a2bad770f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724753014786','1768482874137485238','a2bae116f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724807540737','1768482874137485238','a2bab722f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724857872386','1768482874137485238','a2baea9ef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205724899815426','1768482874137485238','ac47fd90f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205724950147074','1768482874137485238','ac481618f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725000478722','1768482874137485238','ac482d7ef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725046616065','1768482874137485238','ac483846f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725101142017','1768482874137485238','ac484340f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725151473665','1768482874137485238','ac486618f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725201805314','1768482874137485238','ac482248f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725247942658','1768482874137485238','ac48752cf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725302468610','1768482874137485238','b1efac20f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12','188666666666','2025-02-25 10:02:49','N',NULL,NULL,'16663888968888889',1,1),('1894205725344411649','1768482874137485238','b1efc02af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725394743297','1768482874137485238','b1efe41af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725453463553','1768482874137485238','b1eff57cf31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725499600898','1768482874137485238','b1f01e3af31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725549932545','1768482874137485238','b1f006d4f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725591875585','1768482874137485238','b1efcf52f31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('1894205725642207234','1768482874137485238','b1f0338ef31b11ef905d78fb839f9406',1,NULL,'system-default',1,'188666666666','2025-02-25 10:00:12',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('b1e4ac20f31b11ef905d78fbdead0002','1768482874137485238','b1e4ac20f31b11ef905d78fbdead0001',1,NULL,'system-default',1,'188666666666','2026-10-02 23:15:46',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963d54c18e11f18fc3a5e694323b94','1768482874137485248','6c1d7c80bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963dd6c18e11f18fc3a5e694323b94','1768482874137485248','6c1dc370bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963e26c18e11f18fc3a5e694323b94','1768482874137485248','6c1dc398bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963e76c18e11f18fc3a5e694323b94','1768482874137485248','6c1dc3a2bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963ebcc18e11f18fc3a5e694323b94','1768482874137485248','6c1e3512bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963ef8c18e11f18fc3a5e694323b94','1768482874137485248','6c1e3526bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963f3ec18e11f18fc3a5e694323b94','1768482874137485248','6c1e3530bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963f7ac18e11f18fc3a5e694323b94','1768482874137485248','6c1e3544bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963fb6c18e11f18fc3a5e694323b94','1768482874137485248','6c1e354ebf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4963ff2c18e11f18fc3a5e694323b94','1768482874137485248','6c1e3558bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e496402ec18e11f18fc3a5e694323b94','1768482874137485248','6c1e3562bf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e496406ac18e11f18fc3a5e694323b94','1768482874137485248','6c1e356cbf4411f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49640b0c18e11f18fc3a5e694323b94','1768482874137485248','91f5a3f8bfe211f18fc3a5e694323b94',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49743dec18e11f18fc3a5e694323b94','1768482874137485248','1814169974018928641',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974492c18e11f18fc3a5e694323b94','1768482874137485248','9a71582cdf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49744f6c18e11f18fc3a5e694323b94','1768482874137485248','9a717e52df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e497455ac18e11f18fc3a5e694323b94','1768482874137485248','9a717e53df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49745aac18e11f18fc3a5e694323b94','1768482874137485248','9a71a478df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49745e6c18e11f18fc3a5e694323b94','1768482874137485248','9a71a479df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e497462cc18e11f18fc3a5e694323b94','1768482874137485248','9a71ca9edf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974668c18e11f18fc3a5e694323b94','1768482874137485248','9a71f0c4df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49746a4c18e11f18fc3a5e694323b94','1768482874137485248','9a71f0c5df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e49746eac18e11f18fc3a5e694323b94','1768482874137485248','a0ba9526df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974726c18e11f18fc3a5e694323b94','1768482874137485248','a0ba9527df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974762c18e11f18fc3a5e694323b94','1768482874137485248','a0babb4cdf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e497479ec18e11f18fc3a5e694323b94','1768482874137485248','a0bae172df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974848c18e11f18fc3a5e694323b94','1768482874137485248','a0bae173df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974898c18e11f18fc3a5e694323b94','1768482874137485248','a0bb0798df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974910c18e11f18fc3a5e694323b94','1768482874137485248','a0bb0799df9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1),('e4974960c18e11f18fc3a5e694323b94','1768482874137485248','a0bb2dbedf9e11ee9b3d00163e085602',1,NULL,'system-default',1,'system','2026-10-06 22:04:48',NULL,NULL,'N',NULL,NULL,'16663888968888889',0,1);
INSERT INTO `system_organization_app` VALUES ('2107000000000000001','1768482874137485238',1,'16663888968888889','system-default',1,'188666666666','2026-10-05 06:44:04',NULL,NULL,NULL,NULL,'N',NULL),('2107000000000000002','1768482874137485248',1,'16663888968888889','system-default',1,'188666666666','2026-10-05 06:44:04',NULL,NULL,NULL,NULL,'N',NULL);
INSERT INTO `system_dictionary_type` VALUES ('1710583019914629121','性别','SEX',1,'system-default',1,'188666666666','2023-10-07 17:09:08',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1716711670456475650','系统模块','SYSTEM_MODULE',1,'system-default',1,'188666666666','2023-10-24 15:02:12',NULL,NULL,'N',NULL,NULL,'模块的编码','16663888968888889','155555555555'),('1716712018248163329','编码配置类型','CODE_CONFIG_TYPE',1,'system-default',1,'188666666666','2023-10-24 15:03:35','188666666666','2023-10-24 15:46:57','N',NULL,NULL,'编码配置可选类型，固定编码，给后台查询该编码的配置情况','16663888968888889','155555555555'),('1716722866219868161','编码类型','CODE_TYPE',1,'system-default',1,'188666666666','2023-10-24 15:03:35',NULL,NULL,'N',NULL,NULL,'编码的类型:时间、递增、数字ID','16663888968888889','155555555555');
INSERT INTO `system_dictionary_data` VALUES ('1231122411',0,'男','0','SEX',NULL,NULL,'N',1,'system-default',1,'SYSTEM','2023-09-17 07:52:51','188666666666','2024-02-28 14:30:06','N',NULL,NULL,NULL,'16663888968888889','155555555555'),('123445523',0,'女','1','SEX',NULL,NULL,'N',1,'system-default',1,'SYSTEM','2023-09-17 07:53:31','188666666666','2024-02-28 14:30:13','N',NULL,NULL,NULL,'16663888968888889','155555555555'),('1716723130733649922',0,'时间','DATETIME','CODE_TYPE','','','',1,'system-default',1,'188666666666','2023-10-24 15:05:02',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1716723276338913282',0,'数字ID','NUMBER_ID','CODE_TYPE','','','',1,'system-default',1,'188666666666','2023-10-24 15:05:02',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1716723506346156033',0,'递增ID','INCREMENTAL','CODE_TYPE','','','',1,'system-default',1,'188666666666','2023-10-24 15:05:02',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555'),('1762728211723202561',1,'未知','2','SEX','','','',1,'system-default',1,'188666666666','2024-02-28 14:35:30',NULL,NULL,'N',NULL,NULL,'','16663888968888889','155555555555');
INSERT INTO `system_default_config` VALUES ('1209099090','SYSTEM_THEME','{"tab": {"mode": "chrome", "height": 44, "isCache": true, "visible": false, "modeList": [{"label": "谷歌风格", "value": "chrome"}, {"label": "按钮风格", "value": "button"}]}, "menu": {"horizontalPosition": "flex-start", "horizontalPositionList": [{"label": "居左", "value": "flex-start"}, {"label": "居中", "value": "center"}, {"label": "居右", "value": "flex-end"}]}, "page": {"animate": true, "animateMode": "fade-slide", "animateModeList": [{"label": "滑动", "value": "fade-slide"}, {"label": "消退", "value": "fade"}, {"label": "底部消退", "value": "fade-bottom"}, {"label": "缩放消退", "value": "fade-scale"}, {"label": "渐变", "value": "zoom-fade"}, {"label": "闪现", "value": "zoom-out"}]}, "sider": {"width": 180, "inverted": false, "mixWidth": 80, "collapsedWidth": 64, "mixChildMenuWidth": 200, "mixCollapsedWidth": 48}, "footer": {"fixed": false, "right": true, "height": 48, "visible": false, "inverted": false}, "header": {"crumb": {"visible": false, "showIcon": true}, "height": 56, "inverted": false}, "layout": {"mode": "horizontal-mix", "minWidth": 900, "modeList": [{"label": "左侧菜单模式", "value": "vertical"}, {"label": "左侧菜单混合模式", "value": "vertical-mix"}, {"label": "顶部菜单模式", "value": "horizontal"}, {"label": "顶部菜单混合模式", "value": "horizontal-mix"}]}, "darkMode": false, "otherColor": {"info": "#0099ad", "error": "#f5222d", "success": "#52c41a", "warning": "#faad14"}, "scrollMode": "content", "showReload": true, "themeColor": "#165DFF", "scrollModeList": [{"label": "外层滚动", "value": "wrapper"}, {"label": "主体滚动", "value": "content"}], "themeColorList": ["#1890ff", "#409EFF", "#007AFF", "#5ac8fa", "#5856D6", "#536dfe", "#646cff", "#9c27b0", "#AF52DE", "#0096c7", "#00C1D4", "#34C759", "#43a047", "#7cb342", "#c0ca33", "#78DEC7", "#e53935", "#d81b60", "#f4511e", "#fb8c00", "#ffb300", "#fdd835", "#6d4c41", "#546e7a"], "fixedHeaderAndTab": true, "followSystemTheme": false, "isCustomizeInfoColor": false, "isCustomizeDarkModeTransition": false}','平台样式','16663888968888889','system-default',1,'1','2023-09-19 12:01:22',NULL,NULL,'N',NULL,NULL,NULL),('427c486abff911f18fc3a5e694323b94','dynamic.advanced.linkage-log.retention-days','30','联动日志在线保留天数',NULL,'system-default',1,'system','2026-10-04 21:41:10',NULL,NULL,'N',NULL,NULL,NULL),('427c489cbff911f18fc3a5e694323b94','dynamic.advanced.access-log.retention-days','90','外链审计在线保留天数',NULL,'system-default',1,'system','2026-10-04 21:41:10',NULL,NULL,'N',NULL,NULL,NULL);
INSERT INTO `dynamic_script_asset` VALUES ('08b81220c16211f18fc3a5e694323b94',NULL,'biz_number_rule','编号规则','AUTOFILL','GROOVY','// 模板：业务编号生成（前缀 + yyyyMMdd + 当日序号）。序号按同前缀当日已有记录数递增，
// 并发窗口下非严格唯一，严格唯一请叠加复合唯一约束。
// params: tableName、numberField、prefix
def identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }
if (!identOk(params.tableName) || !identOk(params.numberField)) {
  throw new Exception(''表名/列名必须为字母数字下划线'')
}
if (params.prefix == null || params.prefix.toString().contains("''")) {
  throw new Exception(''prefix 不允许包含单引号'')
}
def today = cn.hutool.core.date.DateUtil.format(new java.util.Date(), ''yyyyMMdd'')
def likePrefix = params.prefix.toString() + today
def sql = "SELECT " + params.numberField + " AS v FROM " + params.tableName + " WHERE deleted_flag=''N'' AND " + params.numberField + " LIKE ''" + likePrefix + "%''"
def rows = daoService.querySql(params.tableName, sql)
def maxSeq = 0
for (row in rows) {
  def v = row.v == null ? '''' : row.v.toString()
  if (v.startsWith(likePrefix)) {
    def seq = cn.hutool.core.convert.Convert.toInt(v.substring(likePrefix.length()), 0)
    if (seq > maxSeq) maxSeq = seq
  }
}
def nextSeq = maxSeq + 1
return likePrefix + cn.hutool.core.util.StrUtil.padPre(String.valueOf(nextSeq), 4, ''0'')
','[{"name": "tableName", "type": "STRING", "required": true}, {"name": "numberField", "type": "STRING", "required": true}, {"name": "prefix", "type": "STRING", "required": true}]','OBJECT',15,'DRAFT',1,NULL,'16663888968888889',NULL,'模板种子：编号规则（按当日序号，非严格唯一）',0,'system','2026-10-06 16:43:42.000',NULL,NULL,'N',NULL,NULL),('08b811dac16211f18fc3a5e694323b94',NULL,'conflict_count','冲突计数','CALC','GROOVY','// 模板：同一资源在某时段内的冲突行数。表/列名过标识符白名单，值禁单引号（模板口径，严格场景请改参数化）。
// params: tableName、resourceField、resourceValue、startField/endField、start/end、excludeId（编辑态排除自身）
def identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }
if (!identOk(params.tableName) || !identOk(params.resourceField) || !identOk(params.startField) || !identOk(params.endField)) {
  throw new Exception(''表名/列名必须为字母数字下划线'')
}
if (params.resourceValue == null || params.resourceValue.toString().contains("''")) {
  throw new Exception(''resourceValue 不允许包含单引号'')
}
def sql = "SELECT * FROM " + params.tableName + " WHERE deleted_flag=''N'' AND " + params.resourceField + " = ''" + params.resourceValue + "''"
def rows = daoService.querySql(params.tableName, sql)
def toTime(v) { v == null ? null : (v instanceof java.util.Date ? v : cn.hutool.core.date.DateUtil.parse(v.toString())) }
def s = toTime(params.start); def e = toTime(params.end)
def count = 0
for (row in rows) {
  if (params.excludeId != null && params.excludeId.toString() == String.valueOf(row.id)) continue
  def rs = toTime(row[params.startField]); def re = toTime(row[params.endField])
  if (rs == null || re == null) continue
  if (!(re <= s || e <= rs)) count++
}
return count
','[{"name": "tableName", "type": "STRING", "required": true}, {"name": "resourceField", "type": "STRING", "required": true}, {"name": "resourceValue", "type": "STRING", "required": true}, {"name": "startField", "type": "STRING", "required": true}, {"name": "endField", "type": "STRING", "required": true}, {"name": "start", "type": "STRING", "required": true}, {"name": "end", "type": "STRING", "required": true}, {"name": "excludeId", "type": "STRING", "required": false}]','OBJECT',15,'DRAFT',1,NULL,'16663888968888889',NULL,'模板种子：冲突计数（querySql 口径，标识符白名单 + 值禁单引号）',0,'system','2026-10-06 16:43:42.000',NULL,NULL,'N',NULL,NULL),('08b81144c16211f18fc3a5e694323b94',NULL,'overlap_check','时间重叠判断','VALIDATION','GROOVY','// 模板：时间重叠判断（半开区间 [start,end)）。返回 false 时保存校验挂点会用 fail_message 拦截
// params: startA/endA（基准时段）、startB/endB（待比对时段），STRING（yyyy-MM-dd HH:mm:ss）或 Date
def toTime(v) {
  if (v == null) return null
  if (v instanceof java.util.Date) return v
  return cn.hutool.core.date.DateUtil.parse(v.toString())
}
def a1 = toTime(params.startA); def a2 = toTime(params.endA)
def b1 = toTime(params.startB); def b2 = toTime(params.endB)
if (a1 == null || a2 == null || b1 == null || b2 == null) return false
return !(a2 <= b1 || b2 <= a1)
','[{"name": "startA", "type": "STRING", "required": true, "description": "基准时段开始"}, {"name": "endA", "type": "STRING", "required": true, "description": "基准时段结束"}, {"name": "startB", "type": "STRING", "required": true, "description": "比对时段开始"}, {"name": "endB", "type": "STRING", "required": true, "description": "比对时段结束"}]','BOOLEAN',10,'PUBLISHED',1,NULL,'16663888968888889',NULL,'模板种子：时间重叠（overlap）半开区间口径',0,'system','2026-10-06 16:43:42.000','188666666666','2026-10-06 16:57:00.756','N',NULL,NULL),('60bee154c16411f18fc3a5e694323b94',NULL,'time_range_available','时段可用校验','VALIDATION','GROOVY','// 模板：时段可用性校验（保存校验挂点专用，返回 true=可用/通过）。
// 查询同表同资源的既有预约，判断 [start,end) 与存量时段是否重叠；重叠返回 false 中止保存。
// params: tableName、resourceField、resourceValue、startField/endField、start/end、excludeId（编辑态排除自身）
def identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }
if (!identOk(params.tableName) || !identOk(params.resourceField) || !identOk(params.startField) || !identOk(params.endField)) {
  throw new Exception(''表名/列名必须为字母数字下划线'')
}
if (params.resourceValue == null || params.resourceValue.toString().contains("''")) {
  throw new Exception(''resourceValue 不允许包含单引号'')
}
def sql = "SELECT * FROM " + params.tableName + " WHERE deleted_flag=''N'' AND " + params.resourceField + " = ''" + params.resourceValue + "''"
def rows = daoService.querySql(params.tableName, sql)
def toTime(v) { v == null ? null : (v instanceof java.util.Date ? v : cn.hutool.core.date.DateUtil.parse(v.toString())) }
def s = toTime(params.start); def e = toTime(params.end)
if (s == null || e == null) return true
for (row in rows) {
  if (params.excludeId != null && params.excludeId.toString() == String.valueOf(row.id)) continue
  def rs = toTime(row[params.startField]); def re = toTime(row[params.endField])
  if (rs == null || re == null) continue
  if (!(re <= s || e <= rs)) return false
}
return true
','[{"name": "tableName", "type": "STRING", "required": true}, {"name": "resourceField", "type": "STRING", "required": true}, {"name": "resourceValue", "type": "STRING", "required": true}, {"name": "startField", "type": "STRING", "required": true}, {"name": "endField", "type": "STRING", "required": true}, {"name": "start", "type": "STRING", "required": true}, {"name": "end", "type": "STRING", "required": true}, {"name": "excludeId", "type": "STRING", "required": false}]','BOOLEAN',15,'PUBLISHED',1,NULL,'16663888968888889',NULL,'模板种子：保存校验挂点专用（true=通过）；与 overlap_check 互为补集',0,'system','2026-10-06 17:00:28.000','188666666666','2026-10-06 17:01:53.353','N',NULL,NULL),('08b8120cc16211f18fc3a5e694323b94',NULL,'workday_add','工作日计算','CALC','GROOVY','// 模板：从起始日起加 N 个工作日（周一至周五），节假日表未接入（如需请叠加节假日参数扩展）。
// params: startDate（yyyy-MM-dd）、days（NUMBER）
def base = cn.hutool.core.date.DateUtil.parse(params.startDate.toString())
def n = cn.hutool.core.convert.Convert.toInt(params.days, 0)
def cursor = base
def added = 0
while (added < n) {
  cursor = cn.hutool.core.date.DateUtil.offsetDay(cursor, 1)
  def dow = cn.hutool.core.date.DateUtil.dayOfWeek(cursor)
  if (dow >= 2 && dow <= 6) added++
}
return cn.hutool.core.date.DateUtil.format(cursor, ''yyyy-MM-dd'')
','[{"name": "startDate", "type": "STRING", "required": true}, {"name": "days", "type": "NUMBER", "required": true}]','OBJECT',10,'DRAFT',1,NULL,'16663888968888889',NULL,'模板种子：工作日顺延（未含节假日表）',0,'system','2026-10-06 16:43:42.000',NULL,NULL,'N',NULL,NULL);
INSERT INTO `system_dept_relation` VALUES ('system-default',1,'188666666666','2023-03-02 21:25:52',NULL,NULL,'N',NULL,NULL,'1631284688245706753',NULL,'16663888968888889','155555555555',1),('system-default',1,'188666666666','2023-03-02 21:27:11',NULL,NULL,'N',NULL,NULL,'1631285019985707010',NULL,'16663888968888889','16663888968888889',1),('system-default',1,'188666666666','2023-09-14 11:55:22',NULL,NULL,'N',NULL,NULL,'1702169136837296129',NULL,'16663888968888889','1702169136598220802',2),('system-default',1,'188666666666','2023-09-14 11:55:22',NULL,NULL,'N',NULL,NULL,'1702169136862461953',NULL,'155555555555','1702169136598220802',1),('system-default',1,'188666666666','2023-09-25 15:49:54',NULL,NULL,'N',NULL,NULL,'1706214426926067714',NULL,'16663888968888889','1706214426871541762',1);

INSERT INTO `system_organization_menu`
  (`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`,
   `created_by`, `created_time`, `deleted_flag`)
SELECT lower(hex(randomblob(16))), m.id, NULL, 1, '16663888968888889', 'system-default', 1,
       '188666666666', datetime('now','localtime'), 'N'
FROM `system_menu` m;
INSERT INTO `system_role_menu`
  (`tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `id`,
   `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`)
SELECT 'system-default', 1, '188666666666', datetime('now','localtime'), 'N', lower(hex(randomblob(16))),
       r.id, m.id, m.permission, '50', NULL
FROM `system_role` r CROSS JOIN `system_menu` m;
INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`, `icon`,
 `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`, `tenant_id`, `revision`,
 `created_by`, `created_time`, `deleted_flag`)
SELECT 'da7a00c0f31b11ef905d78fbdead0010', '数据大屏', 'dynamic_datav', 16, 1, '/dynamic/datav', 'C', 'datav:screen:list', 1, 'icon-default', 0,
       'self', NULL, 0, 0,
       (SELECT `id` FROM `system_menu` WHERE `menu_code` = 'dynamic' AND `deleted_flag` = 'N' LIMIT 1), '16663888968888889', 'system-default', 1, '188666666666', datetime('now','localtime'), 'N'
WHERE (SELECT `id` FROM `system_menu` WHERE `menu_code` = 'dynamic' AND `deleted_flag` = 'N' LIMIT 1) IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 'da7a00c0f31b11ef905d78fbdead0010');
INSERT INTO `system_app_menu`
(`id`, `app_id`, `menu_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `must_menu`, `visible_menu`)
SELECT 'da7a00c0f31b11ef905d78fbdead0011', p.app_id, 'da7a00c0f31b11ef905d78fbdead0010', 1, '16663888968888889', 'system-default', 1,
       '188666666666', datetime('now','localtime'), 'N', 0, 1
FROM (SELECT app_id FROM `system_app_menu` WHERE menu_id = (SELECT `id` FROM `system_menu` WHERE `menu_code` = 'dynamic' AND `deleted_flag` = 'N' LIMIT 1) AND `deleted_flag` = 'N' LIMIT 1) p
WHERE (SELECT `id` FROM `system_menu` WHERE `menu_code` = 'dynamic' AND `deleted_flag` = 'N' LIMIT 1) IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_app_menu` WHERE menu_id = 'da7a00c0f31b11ef905d78fbdead0010' AND `deleted_flag` = 'N');
INSERT INTO `system_organization_menu`
(`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`)
SELECT 'da7a00c0f31b11ef905d78fbdead0012', 'da7a00c0f31b11ef905d78fbdead0010', NULL, 1, '16663888968888889', 'system-default', 1,
       '188666666666', datetime('now','localtime'), 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_organization_menu` WHERE menu_id = 'da7a00c0f31b11ef905d78fbdead0010' AND `deleted_flag` = 'N');
INSERT INTO `system_menu`
(`id`, `menu_name`, `menu_code`, `order_num`, `visible`, `path`, `menu_type`, `permission`, `status`,
 `icon`, `app_menu`, `component`, `params`, `is_frame`, `is_cache`, `parent_id`, `organization_id`,
 `tenant_id`, `revision`, `created_by`, `created_time`, `updated_by`, `updated_time`, `remark`,
 `deleted_time`, `deleted_by`, `deleted_flag`)
SELECT lower(hex(randomblob(16))), b.menu_name, '', 1, 1, '', 'F', b.permission, 1,
       'icon-default', 0, '', NULL, 0, 0,
       d.id, d.organization_id, d.tenant_id, 1, d.created_by, datetime('now','localtime'), NULL, NULL, b.remark,
       NULL, NULL, 'N'
FROM (
         SELECT '大屏列表' AS menu_name, 'datav:screen:list' AS permission, '大屏-列表（与 C 菜单同码，判重跳过）' AS remark
         UNION ALL
         SELECT '大屏详情', 'datav:screen:query', '大屏-详情与版本列表'
         UNION ALL
         SELECT '大屏新增', 'datav:screen:add', '大屏-保存与复制'
         UNION ALL
         SELECT '大屏编辑', 'datav:screen:edit', '大屏-发布/下线与版本回滚'
         UNION ALL
         SELECT '大屏删除', 'datav:screen:remove', '大屏-删除'
     ) `b`
         JOIN (SELECT `id`, `organization_id`, `tenant_id`, `created_by`
               FROM `system_menu`
               WHERE `menu_code` IN ('datav', 'dynamic_datav') AND `menu_type` = 'C' AND `deleted_flag` = 'N'
               LIMIT 1) d
WHERE NOT EXISTS (SELECT 1
                  FROM `system_menu` `x`
                  WHERE `x`.`deleted_flag` = 'N'
                    AND `x`.`menu_type` = 'F'
                    AND `x`.`permission` = b.permission);
INSERT INTO `system_role_menu`
(`tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `id`, `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`)
SELECT 'system-default', 1, '188666666666', datetime('now','localtime'), 'N', 'da7a00c0f31b11ef905d78fbdead0013', '12263522227777777',
       'da7a00c0f31b11ef905d78fbdead0010', 'datav:screen:list', 20, NULL
WHERE 'da7a00c0f31b11ef905d78fbdead0010' IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` WHERE menu_id = 'da7a00c0f31b11ef905d78fbdead0010' AND `deleted_flag` = 'N');
INSERT INTO `system_role_menu`
(`id`, `remark`, `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`,
 `tenant_id`, `revision`, `created_by`, `created_time`, `updated_by`, `updated_time`,
 `deleted_flag`, `deleted_by`, `deleted_time`)
SELECT lower(hex(randomblob(16))), NULL, r.role_id, m.menu_id, m.permission, r.data_scope, r.app_id,
       r.tenant_id, 1, r.created_by, datetime('now','localtime'), NULL, NULL, 'N', NULL, NULL
FROM (
         SELECT x.id AS menu_id, x.permission AS permission
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND (x.menu_code IN ('datav', 'dynamic_datav') OR (x.menu_type = 'F' AND x.permission LIKE 'datav:screen:%'))
     ) m
         JOIN system_role_menu r
              ON r.menu_id = (SELECT id FROM system_menu WHERE menu_code IN ('report-designer', 'dynamic_report-designer') AND deleted_flag = 'N' LIMIT 1)
                  AND r.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` e WHERE e.menu_id = m.menu_id AND e.role_id = r.role_id);
INSERT INTO `system_organization_menu`
(`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`)
SELECT lower(hex(randomblob(16))), m.menu_id, NULL, 1, o.organization_id, 'system-default', 1, o.created_by, datetime('now','localtime'), 'N'
FROM (
         SELECT x.id AS menu_id
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND (x.menu_code IN ('datav', 'dynamic_datav') OR (x.menu_type = 'F' AND x.permission LIKE 'datav:screen:%'))
     ) m
         JOIN system_organization_menu o
              ON o.menu_id = (SELECT id FROM system_menu WHERE menu_code IN ('report-designer', 'dynamic_report-designer') AND deleted_flag = 'N' LIMIT 1)
                  AND o.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_organization_menu` e WHERE e.menu_id = m.menu_id AND e.organization_id = o.organization_id);
INSERT INTO `system_role_menu`
(`id`, `remark`, `role_id`, `menu_id`, `permission`, `data_scope`, `app_id`,
 `tenant_id`, `revision`, `created_by`, `created_time`, `updated_by`, `updated_time`,
 `deleted_flag`, `deleted_by`, `deleted_time`)
SELECT lower(hex(randomblob(16))), '大屏默认随附授权', r.role_id, m.menu_id, m.permission, r.data_scope, r.app_id,
       r.tenant_id, 1, r.created_by, datetime('now','localtime'), NULL, NULL, 'N', NULL, NULL
FROM (
         SELECT x.id AS menu_id, x.permission
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND x.menu_type = 'F'
           AND x.permission IN ('report:dataset:list', 'report:dataset:query')
     ) m
         JOIN system_role_menu r
              ON r.menu_id = 'da7a00c0f31b11ef905d78fbdead0010' AND r.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` e WHERE e.menu_id = m.menu_id AND e.role_id = r.role_id);
INSERT INTO `system_organization_menu`
(`id`, `menu_id`, `app_id`, `status`, `organization_id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`)
SELECT lower(hex(randomblob(16))), m.menu_id, o.app_id, 1, o.organization_id, 'system-default', 1, o.created_by, datetime('now','localtime'), 'N'
FROM (
         SELECT x.id AS menu_id
         FROM system_menu x
         WHERE x.deleted_flag = 'N'
           AND x.menu_type = 'F'
           AND x.permission IN ('report:dataset:list', 'report:dataset:query')
     ) m
         JOIN system_organization_menu o
              ON o.menu_id = 'da7a00c0f31b11ef905d78fbdead0010' AND o.deleted_flag = 'N'
WHERE NOT EXISTS (SELECT 1 FROM `system_organization_menu` e WHERE e.menu_id = m.menu_id AND e.organization_id = o.organization_id);
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001001', 'AI 搭建', 'ai', '0', 'M', '/ai', 'basic', NULL,
       'icon-park-outline:robot', 5, 1, 1, 0, 'N', 'system', datetime('now','localtime'), '16663888968888889', 'AI 对话式搭建入口'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai' AND deleted_flag = 'N');
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001002', 'AI 对话搭建', 'ai_build', '2107100000000001001', 'C', '/ai/build', 'self', 'system:aiBuild:use',
       'icon-park-outline:chat', 10, 1, 1, 0, 'N', 'system', datetime('now','localtime'), '16663888968888889', '与 AI 对话生成表单/流程/报表草稿，人工确认发布'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_build' AND deleted_flag = 'N');
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001003', 'AI 模型配置', 'ai_config', '1798986040628969473', 'C', '/ai/config', 'self', 'system:aiConfig:query',
       'icon-park-outline:config', 95, 1, 1, 0, 'N', 'system', datetime('now','localtime'), '16663888968888889', 'B5 模型配置前端（公网/内网、apiKey 密文、数据流向明示）'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_config' AND deleted_flag = 'N');
INSERT INTO system_organization_menu (id, menu_id, app_id, status, organization_id, created_by, created_time, deleted_flag)
SELECT lower(hex(randomblob(16))), m.id, NULL, 1, '16663888968888889', 'system', datetime('now','localtime'), 'N'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_build', 'ai_config') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_organization_menu om
      WHERE om.menu_id = m.id AND om.organization_id = '16663888968888889' AND om.deleted_flag = 'N');
INSERT INTO system_role_menu (id, role_id, menu_id, permission, deleted_flag, created_by, created_time)
SELECT lower(hex(randomblob(16))), '12263522227777777', m.id, COALESCE(m.permission, ''), 'N', 'system', datetime('now','localtime')
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_build', 'ai_config') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = '12263522227777777' AND rm.menu_id = m.id AND rm.deleted_flag = 'N');
INSERT INTO system_app_menu (id, app_id, menu_id, status, tenant_id, revision, created_by, created_time, deleted_flag, organization_id)
SELECT lower(hex(randomblob(16))), '1768482874137485238', m.id, 1, 'system-default', 1, 'system', datetime('now','localtime'), 'N', '16663888968888889'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_build', 'ai_config') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_app_menu am
      WHERE am.app_id = '1768482874137485238' AND am.menu_id = m.id AND am.deleted_flag = 'N');
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001001', 'AI 搭建', 'ai', '0', 'M', '/ai', 'basic', NULL,
       'icon-park-outline:robot', 5, 1, 1, 0, 'N', 'system', datetime('now','localtime'), '16663888968888889', 'AI 对话式搭建入口'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai' AND deleted_flag = 'N');
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001004', 'AI 问数', 'ai_analyze', '2107100000000001001', 'C', '/ai/analyze', 'self', 'system:aiAnalyze:use',
       'mdi:chart-box-plus-outline', 30, 1, 1, 0, 'N', 'system', datetime('now','localtime'), '16663888968888889', '与 AI 对话问数出图，结果可存为报表'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_analyze' AND deleted_flag = 'N');
INSERT INTO system_menu (id, menu_name, menu_code, parent_id, menu_type, path, component, permission,
                         icon, order_num, visible, status, app_menu, deleted_flag, created_by, created_time, organization_id, remark)
SELECT '2107100000000001005', 'AI 问数取数', 'ai_analyze_data', '2107100000000001004', 'F', '', '', 'report:data:analyze',
       '', 1, 1, 1, 0, 'N', 'system', datetime('now','localtime'), '16663888968888889', 'AI 问数取数/存图端点权限（不碰设计态 report:dataset:*）'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE menu_code = 'ai_analyze_data' AND deleted_flag = 'N');
INSERT INTO system_organization_menu (id, menu_id, app_id, status, organization_id, created_by, created_time, deleted_flag)
SELECT lower(hex(randomblob(16))), m.id, NULL, 1, '16663888968888889', 'system', datetime('now','localtime'), 'N'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_analyze', 'ai_analyze_data') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_organization_menu om
      WHERE om.menu_id = m.id AND om.organization_id = '16663888968888889' AND om.deleted_flag = 'N');
INSERT INTO system_role_menu (id, role_id, menu_id, permission, deleted_flag, created_by, created_time)
SELECT lower(hex(randomblob(16))), '12263522227777777', m.id, COALESCE(m.permission, ''), 'N', 'system', datetime('now','localtime')
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_analyze', 'ai_analyze_data') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = '12263522227777777' AND rm.menu_id = m.id AND rm.deleted_flag = 'N');
INSERT INTO system_app_menu (id, app_id, menu_id, status, tenant_id, revision, created_by, created_time, deleted_flag, organization_id)
SELECT lower(hex(randomblob(16))), '1768482874137485248', m.id, 1, 'system-default', 1, 'system', datetime('now','localtime'), 'N', '16663888968888889'
FROM system_menu m
WHERE m.menu_code IN ('ai', 'ai_analyze', 'ai_analyze_data') AND m.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_app_menu am
      WHERE am.app_id = '1768482874137485248' AND am.menu_id = m.id AND am.deleted_flag = 'N');
INSERT INTO system_app_menu (id, app_id, menu_id, status, tenant_id, revision, created_by, created_time, deleted_flag, organization_id)
SELECT lower(hex(randomblob(16))), a.id, m.id, 1, 'system-default', 1, 'system', datetime('now','localtime'), 'N', '16663888968888889'
FROM system_app_info a
JOIN system_menu m ON m.menu_code IN ('ai', 'ai_analyze') AND m.deleted_flag = 'N'
WHERE a.deleted_flag = 'N'
  AND NOT EXISTS (SELECT 1 FROM system_app_menu am
      WHERE am.app_id = a.id AND am.menu_id = m.id AND am.deleted_flag = 'N');
COMMIT;
