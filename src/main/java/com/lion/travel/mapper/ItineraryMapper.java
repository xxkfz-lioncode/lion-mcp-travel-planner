package com.lion.travel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lion.travel.entity.Itinerary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行程 Mapper。
 *
 * <p>继承 {@link BaseMapper} 获得单表 CRUD,用于行程结果的持久化与查询。</p>
 */
@Mapper
public interface ItineraryMapper extends BaseMapper<Itinerary> {
}
