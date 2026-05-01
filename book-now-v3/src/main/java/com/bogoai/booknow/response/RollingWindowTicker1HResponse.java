package com.bogoai.booknow.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.sql.Timestamp;

public class RollingWindowTicker1HResponse {

    @JsonProperty("s")
    private String Symbol;

    @JsonProperty("c")
    private BigDecimal price;

    @JsonProperty("P")
    private double percentage;

    @JsonProperty("E")
    private Timestamp timestamp;

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return Symbol;
    }

    public void setSymbol(String symbol) {
        Symbol = symbol;
    }

    public java.math.BigDecimal getPrice() {
        return price;
    }

    public void setPrice(java.math.BigDecimal price) {
        this.price = price;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(String percentage) {
        this.percentage = Double.parseDouble(percentage);
    }


}
