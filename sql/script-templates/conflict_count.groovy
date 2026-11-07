// 模板：同一资源在某时段内的冲突行数。表/列名过标识符白名单，值禁单引号（模板口径，严格场景请改参数化）。
// params: tableName、resourceField、resourceValue、startField/endField、start/end、excludeId（编辑态排除自身）
def identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }
if (!identOk(params.tableName) || !identOk(params.resourceField) || !identOk(params.startField) || !identOk(params.endField)) {
  throw new Exception('表名/列名必须为字母数字下划线')
}
if (params.resourceValue == null || params.resourceValue.toString().contains("'")) {
  throw new Exception('resourceValue 不允许包含单引号')
}
def sql = "SELECT * FROM " + params.tableName + " WHERE deleted_flag='N' AND " + params.resourceField + " = '" + params.resourceValue + "'"
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
