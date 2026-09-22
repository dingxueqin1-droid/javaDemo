package com.atguigu.lease.web.admin.controller.apartment;


import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.model.entity.LabelInfo;
import com.atguigu.lease.model.enums.ItemType;
import com.atguigu.lease.web.admin.service.LabelInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "标签管理")
@RestController
@RequestMapping("/admin/label")
public class LabelController {

    @Autowired
    private LabelInfoService labelInfoService;

//    public Result labelList(@RequestParam Integer type) {
//        //LambdaQueryWrapper<LabelInfo> wrapper = new LambdaQueryWrapper<>();
//        // type=2
//        //wrapper.eq(LabelInfo::getType,type);
//        QueryWrapper<LabelInfo> wrapper = new QueryWrapper<>();
//        wrapper.eq("type",type);
//
//        List<LabelInfo> list = labelInfoService.list(wrapper);
//        return Result.ok(list);
//    }

    //select * from label_info where type=2
    @Operation(summary = "（根据类型）查询标签列表")
    @GetMapping("list")
    public Result<List<LabelInfo>>
            labelList(@RequestParam(required = true) ItemType itemType) {
        LambdaQueryWrapper<LabelInfo> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(LabelInfo::getType,itemType);

        List<LabelInfo> list = labelInfoService.list(wrapper);
        return Result.ok(list);
    }

    @Operation(summary = "新增或修改标签信息")
    @PostMapping("saveOrUpdate")
    public Result saveOrUpdateLabel(@RequestBody LabelInfo labelInfo) {
        labelInfoService.saveOrUpdate(labelInfo);
        return Result.ok();
    }

    @Operation(summary = "根据id删除标签信息")
    @DeleteMapping("deleteById")
    public Result deleteLabelById(@RequestParam Long id) {
        labelInfoService.removeById(id);
        return Result.ok();
    }

}
