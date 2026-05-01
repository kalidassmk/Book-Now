package com.bogoai.booknow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DustAsset implements Serializable {
    private String asset;
    private String free;
    private String usdtValue;
    private String status = "DUST";
    private long updatedAt;
}
