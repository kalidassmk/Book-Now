package com.bogoai.booknow.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.redis.core.RedisHash;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
@RedisHash("Percentage")
public class HLA implements Serializable {
    private String symbol;
    private double minPercentage;
    private double maxPercentage;
    private double avgPercentage;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal avgPrice;
    private double baseToCurrentInterval;
    private double minInterval;
    private double maxInterval;
    private double avgInterval;
    private int count;
    double minCount;
    double maxCount;
    double avgCount;

}