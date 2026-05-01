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
@RedisHash("Buy")
public class Buy {

    private Percentage percentage;
    private double buyPercentage;
    private BigDecimal buyPrice;
    private String status;
    private double selP;
    private Timestamp buyTimeStamp;
    private String hms;
    private Long orderId;
    private String executedQty;
    private String origQty;
}
