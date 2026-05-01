package com.bogoai.booknow.model;

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
@RedisHash("TimeAnalyse")
public class TimeAnalyse {
    private String type;
    List<ShortestTime> shortestTimeList;
}
