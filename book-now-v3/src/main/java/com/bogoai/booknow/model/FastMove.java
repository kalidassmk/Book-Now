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
@RedisHash("FastMove")
public class FastMove {

    private String symbol;
    private double countG0L1;
    private double countG1L2;
    private double countG2L3;
    private double countG3L5;
    private double countG5L7;
    private double countG7L10;
    private double countG10;
    private double overAllCount;

}
