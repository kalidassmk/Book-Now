package com.bogoai.api.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public enum UniversalTransferType {
    MAIN_MARGIN,    // spot -> margin
    MAIN_UMFUTURE,  // spot -> USDT-margined future
    MAIN_CMFUTURE,  // spot -> coin-margined future
    MARGIN_MAIN,    // margin -> spot
    UMFUTURE_MAIN,  // USDT-margined future -> spot
    CMFUTURE_MAIN,  // coin-margined future -> spot
    MAIN_FUNDING,   // spot -> funding
    FUNDING_MAIN,   // funding -> spot
}
