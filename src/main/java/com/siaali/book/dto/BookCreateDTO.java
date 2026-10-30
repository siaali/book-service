package com.siaali.book.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增图书入参。
 *
 * 为什么不用实体类直接接参：实体里有 id、createTime 这些不该由前端传的字段，
 * 直接接参等于允许前端伪造主键和创建时间。
 */
@Data
public class BookCreateDTO {

    @NotBlank(message = "ISBN 不能为空")
    private String isbn;

    @NotBlank(message = "书名不能为空")
    private String name;

    @NotBlank(message = "作者不能为空")
    private String author;

    private String publisher;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能为负数")
    private BigDecimal price;

    private String type;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer totalStock;
}
