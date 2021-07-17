我们都知道在mybatis中能用`#{}`取参数就不要使用`${}`取，这是因为默认情况下，使用`#{}`格式的语法会让MyBatis创建PreparedStatement参数并安全地设置参数（就像使用？一样）。
这样做更安全，通常也是首选做法。

不过有时你就是想直接在SQL语句中插入一个不转义的字符串，比如，像ORDER BY，你可以这样来使用：`ORDER BY ${COLUMNNAME}`这里的MyBatis不会修改或者转义字符串。

因此使用`${}`会有注入风险，但问题来了，如果`order by #{id}`这样写会导致排序失效，因为此时这个`#{id}`作为一个字符串变量了，类似这样：`order by 'id'`，所以导致无法达到你想要的排序。

我的解决方案有两个：

1. 仍然使用`${}`，但不会拿前端传递过来的参数直接放到拼到`order by`后面，而是在控制层判断转换后用自己定义的值传到mybatis
```java
@ResponseBody
@RequestMapping(value = "/list")
public List<xxx> list(@RequestParam("page") int page
        , @RequestParam("orderBy") String orderBy) {
    PageQuery query = null;
    if ("idDesc".eques(orderBy)) {
      query = new PageQuery(page, 15, "id", "desc");
    }
    List<xxx> list = xxxMapper.find(query);
    return list;
}
```

2. 使用mybatis-plus的排序插件
```java
QueryWrapper<ComOrganization> queryWrapper = new QueryWrapper<ComOrganization>();
//如果前端有值传入 则将其赋值。无则就不赋值。为后面设置查询条件铺路
queryWrapper.eq(param.getCompanyId() != null && param.getCompanyId() != 0, "company_id", param.getCompanyId());
queryWrapper.eq(param.getDepartmentId() != null && param.getDepartmentId() != 0, "department_id", param.getDepartmentId());
//设置查询条件
queryWrapper.select(BeanUtils.getSqlSelect(ComOrganization.class));
//根据id进行顺序排序 id可用替换为你想进行排序的字段
queryWrapper.orderByAsc("id");
IPage<ComOrganization> page = new Page<>(Long.parseLong(param.getPageNumber()), Long.parseLong(param.getPageSize()));
IPage<ComOrganization> poPage = baseMapper.selectPage(page, queryWrapper);
```
