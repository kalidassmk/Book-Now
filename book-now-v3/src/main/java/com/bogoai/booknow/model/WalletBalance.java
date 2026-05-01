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
public class WalletBalance implements Serializable {
    private String asset;
    private String free;
    private String locked;
    private long updatedAt;

    public Double getTotal() {
        return Double.parseDouble(free) + Double.parseDouble(locked);
    }
}
