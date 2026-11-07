// 模板：从起始日起加 N 个工作日（周一至周五），节假日表未接入（如需请叠加节假日参数扩展）。
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
return cn.hutool.core.date.DateUtil.format(cursor, 'yyyy-MM-dd')
