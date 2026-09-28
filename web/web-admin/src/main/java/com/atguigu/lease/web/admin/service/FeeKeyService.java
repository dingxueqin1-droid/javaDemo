package com.atguigu.lease.web.admin.service;

import com.atguigu.lease.model.entity.FeeKey;
import com.atguigu.lease.web.admin.vo.fee.FeeKeyVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author liubo
* @description 针对表【fee_key(杂项费用名称表)】的数据库操作Service
* @createDate 2023-07-24 15:48:00
*/
public interface FeeKeyService extends IService<FeeKey> {

    /**
     * 查询全部杂费名称及其对应的杂费值。
     *
     * @return 按杂费名称分组后的杂费信息
     */
    List<FeeKeyVo> listFeeKeyVo();

    /**
     * 删除杂费名称及其关联的全部杂费值。
     *
     * @param feeKeyId 杂费名称 ID
     */
    void removeFeeKeyById(Long feeKeyId);
}
