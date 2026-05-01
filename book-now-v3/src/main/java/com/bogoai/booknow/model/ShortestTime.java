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
@RedisHash("ShortestTime")
public class ShortestTime implements Comparable<ShortestTime> {

    private String symbol;
    private double timeTook;

    @Override
    public int compareTo(ShortestTime o) {
        return Double.compare(o.timeTook, this.timeTook);
    }

}
