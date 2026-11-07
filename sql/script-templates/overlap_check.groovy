// 模板：时间重叠判断（半开区间 [start,end)）。返回 false 时保存校验挂点会用 fail_message 拦截
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
