package com.bogoai.booknow.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SymbolRule {
    private String symbol;
    private BigDecimal stepSize;
    private BigDecimal tickSize;
    private BigDecimal minQty;
    private BigDecimal maxQty;
    private BigDecimal minNotional;
}
