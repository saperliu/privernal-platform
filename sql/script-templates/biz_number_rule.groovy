// 模板：业务编号生成（前缀 + yyyyMMdd + 当日序号）。序号按同前缀当日已有记录数递增，
// 并发窗口下非严格唯一，严格唯一请叠加复合唯一约束。
// params: tableName、numberField、prefix
def identOk = { s -> s != null && s ==~ /[A-Za-z0-9_]+/ }
if (!identOk(params.tableName) || !identOk(params.numberField)) {
  throw new Exception('表名/列名必须为字母数字下划线')
}
if (params.prefix == null || params.prefix.toString().contains("'")) {
  throw new Exception('prefix 不允许包含单引号')
}
def today = cn.hutool.core.date.DateUtil.format(new java.util.Date(), 'yyyyMMdd')
def likePrefix = params.prefix.toString() + today
def sql = "SELECT " + params.numberField + " AS v FROM " + params.tableName + " WHERE deleted_flag='N' AND " + params.numberField + " LIKE '" + likePrefix + "%'"
def rows = daoService.querySql(params.tableName, sql)
def maxSeq = 0
for (row in rows) {
  def v = row.v == null ? '' : row.v.toString()
  if (v.startsWith(likePrefix)) {
    def seq = cn.hutool.core.convert.Convert.toInt(v.substring(likePrefix.length()), 0)
    if (seq > maxSeq) maxSeq = seq
  }
}
def nextSeq = maxSeq + 1
return likePrefix + cn.hutool.core.util.StrUtil.padPre(String.valueOf(nextSeq), 4, '0')
