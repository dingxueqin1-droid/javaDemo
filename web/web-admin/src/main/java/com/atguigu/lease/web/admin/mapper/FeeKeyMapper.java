package com.atguigu.lease.web.admin.mapper;

import com.atguigu.lease.model.entity.FeeKey;
import com.atguigu.lease.web.admin.vo.fee.FeeKeyVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author liubo
* @description 针对表【fee_key(杂项费用名称表)】的数据库操作Mapper
* @createDate 2023-07-24 15:48:00
* @Entity com.atguigu.lease.model.FeeKey
*/
public interface FeeKeyMapper extends BaseMapper<FeeKey> {

    /**
     * 查询杂费名称及其关联的杂费值。
     *
     * @return 杂费名称列表
     */
    List<FeeKeyVo> selectFeeKeyVoList();
}




