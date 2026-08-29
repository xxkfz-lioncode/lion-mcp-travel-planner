package com.lion.travel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lion.travel.entity.Attraction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 景点 Mapper。
 *
 * <p>继承 {@link BaseMapper} 获得单表 CRUD;复杂检索使用 LambdaQueryWrapper 完成,
 * 无需编写 XML。</p>
 */
@Mapper
public interface AttractionMapper extends BaseMapper<Attraction> {
}
