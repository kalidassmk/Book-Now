package com.bogoai.booknow.model;


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
@RedisHash("ROne")
public class ROne extends R {
    private String symbol;
    private String type;
    private double zeroToOne;
    private double OneToTwo;
    private double twoToThree;
    private String hms;
}
