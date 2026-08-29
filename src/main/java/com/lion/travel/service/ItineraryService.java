package com.lion.travel.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lion.travel.entity.Itinerary;
import com.lion.travel.mapper.ItineraryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 行程服务:负责行程结果的持久化与缓存。
 *
 * <p>生成的行程会写入 t_itinerary 表持久化,并通过 Redisson(RMapCache)
 * 做短期缓存,相同参数重复生成时直接命中缓存,节省大模型调用成本。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryService {

    private final ItineraryMapper itineraryMapper;
    private final RedissonClient redissonClient;

    /** 缓存过期秒数,来自配置 travel.cache-ttl-seconds */
    @Value("${travel.cache-ttl-seconds:3600}")
    private long cacheTtlSeconds;

    /** 缓存 Map 名称 */
    private static final String CACHE_NAME = "travel:itinerary";

    /** 缓存 Key 前缀 */
    private static final String CACHE_PREFIX = "travel:itinerary:";

    /**
     * 保存行程到数据库,并写入 Redisson 缓存。
     *
     * @return 持久化后的行程(含自增主键)
     */
    public Itinerary save(Itinerary itinerary) {
        itinerary.setStatus(1);
        itineraryMapper.insert(itinerary);
        // 写入缓存:key 为 城市+天数+风格 的指纹
        RMapCache<String, String> cache = redissonClient.getMapCache(CACHE_NAME);
        cache.put(buildCacheKey(itinerary.getCity(), itinerary.getDays(), itinerary.getStyle()),
                itinerary.getContent(), cacheTtlSeconds, TimeUnit.SECONDS);
        return itinerary;
    }

    /**
     * 尝试从缓存获取行程内容;未命中返回 null。
     */
    public String getCachedContent(String city, Integer days, String style) {
        RMapCache<String, String> cache = redissonClient.getMapCache(CACHE_NAME);
        return cache.get(buildCacheKey(city, days, style));
    }

    /**
     * 按主键查询行程。
     */
    public Itinerary getById(Long id) {
        return itineraryMapper.selectById(id);
    }

    /**
     * 分页查询历史行程(按创建时间倒序)。
     *
     * @param city 可选,按城市过滤
     */
    public List<Itinerary> list(String city, Integer limit) {
        LambdaQueryWrapper<Itinerary> wrapper = new LambdaQueryWrapper<Itinerary>()
                .eq(StrUtil.isNotBlank(city), Itinerary::getCity, city)
                .orderByDesc(Itinerary::getCreateTime)
                .last("LIMIT " + (limit == null ? 50 : Math.min(limit, 100)));
        return itineraryMapper.selectList(wrapper);
    }

    /**
     * 构建缓存 Key,格式:travel:itinerary:{city}:{days}:{style}
     */
    private String buildCacheKey(String city, Integer days, String style) {
        return CACHE_PREFIX + StrUtil.nullToEmpty(city) + ":"
                + (days == null ? "0" : days) + ":"
                + StrUtil.nullToEmpty(style);
    }
}
