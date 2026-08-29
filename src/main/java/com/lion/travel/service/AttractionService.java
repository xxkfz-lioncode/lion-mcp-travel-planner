package com.lion.travel.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lion.travel.entity.Attraction;
import com.lion.travel.mapper.AttractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 景点服务:负责景点数据的检索。
 *
 * <p>供两类调用方使用:</p>
 * <ul>
 *   <li>REST Controller / MCP 工具:直接查询景点</li>
 *   <li>Graph 工作流中的「景点检索」节点:按城市/标签检索后喂给大模型</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttractionService {

    private final AttractionMapper attractionMapper;

    /**
     * 按城市与关键词检索景点(两条件均可选)。
     *
     * @param city    城市,为空表示不限城市
     * @param keyword 关键词(匹配名称/描述/标签),为空表示不限制
     * @return 符合条件的景点列表
     */
    public List<Attraction> search(String city, String keyword) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<>();
        // 城市精确匹配(可包含)
        if (StrUtil.isNotBlank(city)) {
            wrapper.eq(Attraction::getCity, city.trim());
        }
        // 关键词模糊匹配:名称 / 简介 / 标签
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w
                    .like(Attraction::getName, kw)
                    .or().like(Attraction::getDescription, kw)
                    .or().like(Attraction::getTags, kw));
        }
        // 按评分降序,评分相同按名称排序
        wrapper.orderByDesc(Attraction::getRating)
               .orderByAsc(Attraction::getName);
        return attractionMapper.selectList(wrapper);
    }

    /**
     * 按城市检索,并按评分取前 N 个(供 Graph 行程生成使用)。
     *
     * @param city 城市
     * @param topN 返回条数上限
     */
    public List<Attraction> searchTopByCity(String city, int topN) {
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<Attraction>()
                .eq(Attraction::getCity, city)
                .orderByDesc(Attraction::getRating)
                .last("LIMIT " + topN);
        return attractionMapper.selectList(wrapper);
    }

    /**
     * 获取所有出现过的城市列表(用于前端下拉推荐)。
     */
    public List<String> listCities() {
        return attractionMapper.selectList(new LambdaQueryWrapper<Attraction>()
                        .select(Attraction::getCity)
                        .groupBy(Attraction::getCity))
                .stream()
                .map(Attraction::getCity)
                .toList();
    }
}
