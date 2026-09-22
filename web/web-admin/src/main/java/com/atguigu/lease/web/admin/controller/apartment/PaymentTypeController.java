package com.atguigu.lease.web.admin.controller.apartment;


import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.model.entity.PaymentType;
import com.atguigu.lease.web.admin.service.PaymentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "支付方式管理")
@RequestMapping("/admin/payment")
@RestController
public class PaymentTypeController {

    //注入service
    @Autowired
    private PaymentTypeService paymentTypeService;

    //使用统一结果类，进行返回就可以了

    //查询所有支付类型
//    @Operation(summary = "查询所有支付类型")
//    @GetMapping("list")
//    public List<PaymentType> listPaymentType() {
//        List<PaymentType> list = paymentTypeService.list();
//        return list;
//    }
    //修改-根据id查询
    // 路径传递 /getPaymentType/123
//    @Operation(summary = "根据id查询")
//    @GetMapping("/getPaymentType/{id}")
//    public PaymentType getPaymentType(@PathVariable Long id) {
//        PaymentType paymentType = paymentTypeService.getById(id);
//        return paymentType;
//    }

    @Operation(summary = "统一结果：查询所有支付类型")
    @GetMapping("list")
    public Result listPaymentType() {
        List<PaymentType> list = paymentTypeService.list();

//        Result result = new Result();
//        result.setCode(200);
//        result.setMessage("成功");
//        result.setData(list);
//        return result;
        return Result.ok(list);
    }

    @Operation(summary = "统一结果：根据id查询")
    @GetMapping("/getPaymentType/{id}")
    public Result getPaymentType(@PathVariable Long id) {
        PaymentType paymentType = paymentTypeService.getById(id);

//        Result result = new Result();
//        result.setCode(200);
//        result.setMessage("成功");
//        result.setData(paymentType);
//        return result;
        return Result.ok(paymentType);
    }

    //  PaymentType paymentType   ?name=11&payMonthCount=11
    // @RequestBody PaymentType paymentType
    // @RequestBody 使用json格式传递数据，不能是get提交
    //保存或者更新支付方式
    @Operation(summary = "保存或更新支付方式")
    @PostMapping("saveOrUpdate")
    public Result saveOrUpdate(@RequestBody PaymentType paymentType) {
        //mp在service封装方法 saveOrUpdate，可以实现保存，也可以实现修改
        // saveOrUpdate方法判断传入对象里面是否有id，如果有id做修改，没有id做添加
        boolean isSuccess = paymentTypeService.saveOrUpdate(paymentType);
        if(isSuccess) {
            return Result.ok();
        } else {
            return Result.fail();
        }
    }

    @Operation(summary = "根据ID删除支付方式")
    @DeleteMapping("deleteById")
    public Result deleteById(@RequestParam Long id) {
        boolean remove = paymentTypeService.removeById(id);
        if(remove) {
            return Result.ok();
        } else {
            return Result.fail();
        }
    }
}















