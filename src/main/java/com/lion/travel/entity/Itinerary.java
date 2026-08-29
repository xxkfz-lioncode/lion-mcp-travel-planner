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
 * 旅行行程实体,对应表 t_itinerary。
 *
 * <p>保存 Graph 工作流生成的行程规划结果,便于历史查询与复用。</p>
 */
@Data
@TableName("t_itinerary")
public class Itinerary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(数据库自增) */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标城市 */
    private String city;

    /** 行程天数 */
    private Integer days;

    /** 预算(元) */
    private Double budget;

    /** 出行风格:亲子/情侣/独自/老年/穷游等 */
    private String style;

    /** 行程内容(Markdown 文本) */
    private String content;

    /** 状态:1 正常,0 已删除(逻辑删除预留) */
    private Integer status;

    /** 创建时间(MyBatis-Plus 自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间(MyBatis-Plus 自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
