package com.bogoai.booknow.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.redis.core.RedisHash;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
@RedisHash("CurrentPrice")
public class CurrentPrice implements Serializable {
    private String symbol;
    private double percentage;
    private BigDecimal price;
    private Timestamp timestamp;
    private String hms;
    private double healthIndex; // Weighted score from external signals

    // 5-argument constructor for backward compatibility
    public CurrentPrice(String symbol, double percentage, BigDecimal price, Timestamp timestamp, String hms) {
        this.symbol = symbol;
        this.percentage = percentage;
        this.price = price;
        this.timestamp = timestamp;
        this.hms = hms;
        this.healthIndex = 0.0;
    }
}
