package com.bogoai.booknow.model;

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
@RedisHash("PreviousPercentage")
public class PreviousPercentage implements Comparable<PreviousPercentage> {
    private double lPercentage;
    private double cPercentage;
    private double iPercentage;
    private BigDecimal basePrice;
    private BigDecimal currentPrice;
    private BigDecimal increasedPrice;
    private Timestamp timestamp;

    @Override
    public int compareTo(PreviousPercentage o) {
        return this.timestamp.compareTo(o.timestamp);
    }

}
