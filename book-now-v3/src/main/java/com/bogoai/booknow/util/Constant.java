package com.bogoai.booknow.util;

/**
 * Application-wide constants for Redis keys and configuration defaults.
 * All Redis hash-key names are centralised here to avoid magic strings.
 */
public interface Constant {

    // ── Core price tracking ──────────────────────────────────────────────────
    String CURRENT_PRICE  = "CURRENT_PRICE";
    String RW_BASE_PRICE  = "RW_BASE_PRICE";

    // ── Percentage-gain buckets (base → current) ────────────────────────────
    String BUCKET_G0L1  = ">0<1";
    String BUCKET_G1L2  = ">1<2";
    String BUCKET_G2L3  = ">2<3";
    String BUCKET_G3L5  = ">3<5";
    String BUCKET_G5L7  = ">5<7";
    String BUCKET_G7L10 = ">7<10";
    String BUCKET_G10   = ">10";

    // ── Watch-list keys ──────────────────────────────────────────────────────
    String WATCH_ALL              = "BASE_CURRENT_INC_%";
    String WATCH_PREFIX           = "BS_TO_";       // + bucket + "_INC_%"
    String WATCH_SUFFIX           = "_INC_%";

    // ── Fast-move / momentum ─────────────────────────────────────────────────
    String FAST_MOVE             = "FAST_MOVE";
    String FAST_MOVE_TOP5        = "FM-5";

    // ── Transition-time store keys (outer = group, inner = label) ────────────
    String ST0 = "ST0";   // transitions from base (0%)
    String ST1 = "ST1";   // transitions from 1%
    String ST2 = "ST2";   // transitions from 2%
    String ST3 = "ST3";   // transitions from 3%

    // ── Speed labels ─────────────────────────────────────────────────────────
    String SUPER_FAST_2_3        = "SUPER_FAST>2<3";
    String ULTRA_FAST_3_5        = "ULTRA_FAST>3<5";
    String ULTRA_SUPER_FAST_5_7  = "ULTRA_SUPER_FAST>5<7";
    String LT2MIN_0_TO_3         = "LT2MIN_0>3";
    String ULTRA_FAST_0_TO_2     = "ULTRA_FAST0>2";
    String ULTRA_FAST_2_TO_3     = "ULTRA_FAST2>3";
    String ULTRA_FAST_0_TO_3     = "ULTRA_FAST0>3";

    // ── Rule result keys ─────────────────────────────────────────────────────
    String RULE_1        = "R1";
    String RULE_1_HIT    = "R1P3";
    String RULE_2        = "R2";
    String RULE_2_HIT    = "R2P4";
    String RULE_3        = "R3";
    String RULE_3_HIT    = "R3P4";

    // ── Buy / Sell ────────────────────────────────────────────────────────────
    String BUY_KEY  = "BUY";
    String SELL_KEY = "SELL";   // Hash: symbol → SellRecord (JSON)


    // ── Trade defaults (override via application.properties) ─────────────────
    double BUY_AMOUNT_USDT = 12.0;

    // ── Sell-profit targets per rule ─────────────────────────────────────────
    double SELL_PCT_RULE_1_FULL  = 5.0;
    double SELL_PCT_RULE_1_FAST  = 3.5;
    double SELL_PCT_RULE_2       = 7.0;
    double SELL_PCT_RULE_3       = 9.0;
}
