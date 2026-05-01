package com.bogoai.booknow.model;

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
@RedisHash("Sell")
public class Sell {
    private Buy buy;
    private double sellLeastPercentage;
    private double sellAveragePercentage;
    private double sellMaxPercentage;
    private String sellingPoint;
    private String status;
    private Timestamp timestamp;
}
