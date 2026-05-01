package com.bogoai.booknow.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
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
public class Percentage implements Serializable {
    private String symbol;
    private double basePercentage;
    private double currentPercentage;
    private double increasedPercentage;
    private BigDecimal basePrice;
    private BigDecimal currentPrice;
    private BigDecimal increasedPrice;
    private Timestamp baseTimeStamp;
    private Timestamp currentTimeStamp;
    private double baseToCurrentInterval;
    List<PreviousPercentage> previousCountList;
    private String hms;


}
