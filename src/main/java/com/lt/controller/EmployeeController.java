package com.lt.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.lt.bean.Employee;
import com.lt.utils.Msg;
import com.lt.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理员工CRUD请求
 */
@Controller
public class EmployeeController {

    @Autowired
    EmployeeService employeeService;

    /**
     * 单个批量二合一
     * 批量:1-2-3
     * 单个删除: 1
     * @param ids
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/emp/{ids}",method = RequestMethod.DELETE)
    public Msg deleteEmpById(@PathVariable("ids") String ids){
        //批量删除
        if(ids.contains("-")){
            List<Integer> del_ids=new ArrayList<>();
            String[] str_ids = ids.split("-");
            //组装id的集合
            for (String s:str_ids){
                del_ids.add(Integer.parseInt(s));
            }
            employeeService.deleteBatch(del_ids);
        }else {
            Integer id=Integer.parseInt(ids);
            employeeService.deleteEmp(id);
        }
        return Msg.success();
    }

    /**
     * 如果直接发送ajax=PUT形式的请求
     * 封装的数据
     * Employee
     * Employee{empId=1013, empName='null', gender='null', email='null', dId=null}
     *
     * 问题:
     * 请求体中有数据；
     * 但是Employee对象封装不上；
     * update tab_emp where emp_id =1014
     *
     * 原因:
     * Tomcat：
     * 1、将请求体中的数据，封装一个map
     * 2、request.getParameter("empName")然后就会从这个map中取值
     * 3、SpringMVC封装POJO对象的时候。
     *      会把POJO中每个属性的值，request.getParameter("email);
     *
     * AJAX发送PUT请求引发的血案:
     *      PUT请求，请求体中的数据，request.getParameter("")拿不到
     *      Tomcat一看是PUT不会封装请求体中的数据为map,只有POST形式的请求才封装请求体为map
     *
     *   org.apache.catalina.connector.Request--parseParameters(tomcat源码3111)
     *  protected String parseBodyMethods ="POST";
     *  if(!getConnector().isParseBodyMethod(getMethod())){
     *      success=true;
     *      return;
     *  }
     *
     *  解决方案:
     *  我们要能支持直接发送PUT之类的请求还要封装请求体中的数据
     *  配置上HttpPutFormContentFilter(Spring5.1之后为FormContentFilter)
     *  作用:将请求体中的数据解析包装成一个map。
     *  request被重新包装，request.getParameter()被重写，就会从自己封装的map中去数据
     * 员工更新方法
     * @param employee
     * @return
     */
    @RequestMapping(value = "/emp/{empId}",method = RequestMethod.PUT)
    @ResponseBody
    public Msg saveEmp(Employee employee){
        System.out.println(employee);
        employeeService.updateEmp(employee);
        return Msg.success();
    }


    /**
     * 根据id查询员工
     * @param id
     * @return
     */
    @RequestMapping(value = "/emp/{id}",method = RequestMethod.GET)
    @ResponseBody
    public Msg getEmp(@PathVariable("id") Integer id){
        Employee employee=employeeService.getEmp(id);
        return Msg.success().add("emp",employee);
    }

    /**
     * 检查用户名是否可用
     * @param empName
     * @return
     */
    @RequestMapping(value="/checkuser",method = RequestMethod.POST)
    @ResponseBody
    public Msg checkUser(@RequestParam("empName") String empName){
        //判断用户名是否是合法的表达式
        String regx="(^[a-zA-Z0-9_-]{6,16}$)|(^[\\u2E80-\\u9FFF]{2,5})";
        if (!empName.matches(regx)){
            return Msg.fail().add("va_msg","用户名可以是2-5位中文或者6-16位英文和数字的组合");
        }

        //数据库用户名重复校验
        boolean b=employeeService.checkUser(empName);
        if (b){
            return Msg.success();
        }else {
            return Msg.fail().add("va_msg","用户名不可用");
        }
    }


    /**
     * 员工保存
     * 1、支持JSR303校验
     * 2、导入Hibernate-Validator
     * @return
     */
    @RequestMapping(value = "/emp",method = RequestMethod.POST)
    @ResponseBody
    public Msg saveEmp(@Valid Employee employee, BindingResult result){
        if (result.hasErrors()){
            //校验失败，员工返回失败，在模态框中显示检验失败的错误信息
            Map<String,Object> map=new HashMap<>();
            List<FieldError> errors=result.getFieldErrors();
            for (FieldError fieldError:errors){
                System.out.println("错误的字段名,"+fieldError.getField());
                System.out.println("错误的字信息,"+fieldError.getDefaultMessage());
                map.put(fieldError.getField(),fieldError.getDefaultMessage());
            }
            return Msg.fail().add("errorFields",map);
        }else {
            employeeService.saveEmp(employee);
            return Msg.success();
        }
    }


    /**
     * ResponseBody注解能够正常使用,需要导入jackson包
     * @param pn
     * @return
     */
    @RequestMapping("/emps")
    @ResponseBody
    public Msg getEmpWithJson(@RequestParam(value = "pn",defaultValue = "1")Integer pn){
        //引入PageHelper分页插件
        //在查询之前只需要调用,传入页码，以及分页每页的大小
        PageHelper.startPage(pn,5);
        //startPage后面紧跟的查询就是分页查询
        List<Employee> emps=employeeService.getEmps();
        //使用pageInfo包装查询后的结果，只需要将pageInfo交给页面就行了
        //封装了详细的分页信息，包括有欧美查询出来的数据,传入连续显示的页数
        PageInfo page = new PageInfo(emps,5);

        return Msg.success().add("pageInfo",page);
    }

    /**
     * 查询员工数据(分页查询)
     * 传统的结果存入model的模式
     * @return
     */
    //@RequestMapping("/emps")
    public String getEmps(@RequestParam(value = "pn",defaultValue = "1")Integer pn, Model model){
        //引入PageHelper分页插件
        //在查询之前只需要调用,传入页码，以及分页每页的大小
        PageHelper.startPage(pn,5);
        //startPage后面紧跟的查询就是分页查询
        List<Employee> emps=employeeService.getEmps();
        //使用pageInfo包装查询后的结果，只需要将pageInfo交给页面就行了
        //封装了详细的分页信息，包括有欧美查询出来的数据,传入连续显示的页数
        PageInfo page = new PageInfo(emps,5);
        model.addAttribute("pageInfo",page);

        return "list";
    }
}
