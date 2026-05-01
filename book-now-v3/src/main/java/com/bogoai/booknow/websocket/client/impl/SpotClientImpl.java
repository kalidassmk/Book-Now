package com.bogoai.booknow.websocket.client.impl;

import com.bogoai.booknow.websocket.client.impl.spot.Blvt;
import com.bogoai.booknow.websocket.client.impl.spot.BSwap;
import com.bogoai.booknow.websocket.client.impl.spot.C2C;
import com.bogoai.booknow.websocket.client.impl.spot.Convert;
import com.bogoai.booknow.websocket.client.impl.spot.CryptoLoans;
import com.bogoai.booknow.websocket.client.impl.spot.Fiat;
import com.bogoai.booknow.websocket.client.impl.spot.Futures;
import com.bogoai.booknow.websocket.client.impl.spot.GiftCard;
import com.bogoai.booknow.websocket.client.impl.spot.Market;
import com.bogoai.booknow.websocket.client.impl.spot.Margin;
import com.bogoai.booknow.websocket.client.impl.spot.Mining;
import com.bogoai.booknow.websocket.client.impl.spot.NFT;
import com.bogoai.booknow.websocket.client.impl.spot.Pay;
import com.bogoai.booknow.websocket.client.impl.spot.PortfolioMargin;
import com.bogoai.booknow.websocket.client.impl.spot.Rebate;
import com.bogoai.booknow.websocket.client.impl.spot.Savings;
import com.bogoai.booknow.websocket.client.impl.spot.SubAccount;
import com.bogoai.booknow.websocket.client.impl.spot.Trade;
import com.bogoai.booknow.websocket.client.impl.spot.UserData;
import com.bogoai.booknow.websocket.client.impl.spot.Wallet;
import com.bogoai.booknow.websocket.client.SpotClient;
import com.bogoai.booknow.websocket.client.utils.HmacSignatureGenerator;
import com.bogoai.booknow.websocket.client.utils.SignatureGenerator;

import static com.bogoai.booknow.websocket.client.enums.DefaultUrls.*;
import static com.bogoai.booknow.websocket.client.enums.DefaultUrls.PROD_URL;


public class SpotClientImpl implements SpotClient {
    private final String apiKey;
    private final SignatureGenerator signatureGenerator;
    private final String baseUrl;
    private boolean showLimitUsage = false;

    public SpotClientImpl() {
        this(PROD_URL);
    }

    public SpotClientImpl(String baseUrl) {
        this("", "", baseUrl);
    }

    public SpotClientImpl(String baseUrl, boolean showLimitUsage) {
        this(baseUrl);
        this.showLimitUsage = showLimitUsage;
    }

    public SpotClientImpl(String apiKey, String secretKey) {
        this(apiKey, secretKey, PROD_URL);
    }

    public SpotClientImpl(String apiKey, String secretKey, String baseUrl) {
        this(apiKey, new HmacSignatureGenerator(secretKey), baseUrl);
    }

    public SpotClientImpl(String apiKey, SignatureGenerator signatureGenerator, String baseUrl) {
        this.apiKey = apiKey;
        this.signatureGenerator = signatureGenerator;
        this.baseUrl = baseUrl;
    }

    public void setShowLimitUsage(boolean showLimitUsage) {
        this.showLimitUsage = showLimitUsage;
    }

    @Override
    public Blvt createBlvt() {
        return new Blvt(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public BSwap createBswap() {
        return new BSwap(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public C2C createC2C() {
        return new C2C(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public Convert createConvert() {
        return new Convert(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public CryptoLoans createCryptoLoans() {
        return new CryptoLoans(baseUrl, apiKey, signatureGenerator, showLimitUsage); }

    @Override
    public Fiat createFiat() {
        return new Fiat(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public Futures createFutures() {
        return new Futures(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public GiftCard createGiftCard() {
        return new GiftCard(baseUrl, apiKey, signatureGenerator, showLimitUsage); }

    @Override
    public Margin createMargin() {
        return new Margin(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public Market createMarket() {
        return new Market(baseUrl, apiKey, showLimitUsage);
    }

    @Override
    public Mining createMining() {
        return new Mining(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public NFT createNFT() {
        return new NFT(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public Pay createPay() {
        return new Pay(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public PortfolioMargin createPortfolioMargin() {
        return new PortfolioMargin(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public Rebate createRebate() {
        return new Rebate(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public Savings createSavings() {
        return new Savings(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public com.bogoai.booknow.websocket.client.impl.spot.Staking createStaking() {
        return new com.bogoai.booknow.websocket.client.impl.spot.Staking(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public SubAccount createSubAccount() {
        return new SubAccount(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public Trade createTrade() {
        return new Trade(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }

    @Override
    public UserData createUserData() {
        return new UserData(baseUrl, apiKey, showLimitUsage);
    }

    @Override
    public Wallet createWallet() {
        return new Wallet(baseUrl, apiKey, signatureGenerator, showLimitUsage);
    }
}
