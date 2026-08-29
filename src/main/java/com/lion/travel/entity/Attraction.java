package com.lion.travel.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 景点实体,对应表 t_attraction。
 *
 * <p>用于保存景点基础信息,供「景点检索」与 Graph 工作流中的行程生成使用。</p>
 */
@Data
@TableName("t_attraction")
public class Attraction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(数据库自增) */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景点名称 */
    private String name;

    /** 所在城市 */
    private String city;

    /** 景点类别:自然风光/历史人文/主题乐园/美食街区等 */
    private String category;

    /** 评分(0-5) */
    private Double rating;

    /** 人均消费(元) */
    private Double price;

    /** 景点简介 */
    private String description;

    /** 标签(逗号分隔,如:亲子,夜景,免费) */
    private String tags;

    /** 开放时间(如:08:00-18:00) */
    private String openTime;

    /** 详细地址 */
    private String address;

    /** 创建时间(MyBatis-Plus 自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间(MyBatis-Plus 自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
