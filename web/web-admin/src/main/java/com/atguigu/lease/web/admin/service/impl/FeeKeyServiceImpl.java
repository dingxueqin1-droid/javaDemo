package com.atguigu.lease.web.admin.service.impl;

import com.atguigu.lease.model.entity.FeeKey;
import com.atguigu.lease.model.entity.FeeValue;
import com.atguigu.lease.web.admin.mapper.FeeKeyMapper;
import com.atguigu.lease.web.admin.service.FeeKeyService;
import com.atguigu.lease.web.admin.service.FeeValueService;
import com.atguigu.lease.web.admin.vo.fee.FeeKeyVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
* @author liubo
* @description 针对表【fee_key(杂项费用名称表)】的数据库操作Service实现
* @createDate 2023-07-24 15:48:00
*/
@Service
public class FeeKeyServiceImpl extends ServiceImpl<FeeKeyMapper, FeeKey>
    implements FeeKeyService{

    @Autowired
    private FeeValueService feeValueService;

    @Override
    public List<FeeKeyVo> listFeeKeyVo() {
        return baseMapper.selectFeeKeyVoList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFeeKeyById(Long feeKeyId) {
        feeValueService.remove(
                new LambdaQueryWrapper<FeeValue>()
                        .eq(FeeValue::getFeeKeyId, feeKeyId));
        removeById(feeKeyId);
    }
}




