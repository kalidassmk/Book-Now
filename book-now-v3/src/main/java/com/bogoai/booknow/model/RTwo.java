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
@RedisHash("RTwo")
public class RTwo extends R {
    private String symbol;
    private String type;
    private double OneToTwo;
    private double twoToThree;
    private double threeToFive;
    private String hms;
}
