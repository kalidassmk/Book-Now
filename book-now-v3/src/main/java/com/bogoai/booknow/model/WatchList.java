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
@RedisHash("WatchList")
public class WatchList implements Comparable<WatchList> {

    private String symbol;
    private double basePercentage;
    private double currentPercentage;
    private double increasedPercentage;
    private BigDecimal basePrice;
    private BigDecimal currentPrice;
    private BigDecimal increasedPrice;
    private double count;
    private Timestamp timestamp;
    private String hms;



    @Override
    public int compareTo(WatchList o) {
        return this.timestamp.compareTo(o.timestamp);
    }
}
