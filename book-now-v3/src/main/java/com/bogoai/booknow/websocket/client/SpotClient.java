package com.bogoai.booknow.websocket.client;

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
import com.bogoai.booknow.websocket.client.impl.spot.Staking;
import com.bogoai.booknow.websocket.client.impl.spot.SubAccount;
import com.bogoai.booknow.websocket.client.impl.spot.Trade;
import com.bogoai.booknow.websocket.client.impl.spot.UserData;
import com.bogoai.booknow.websocket.client.impl.spot.Wallet;


public interface SpotClient {
    Blvt createBlvt();

    BSwap createBswap();

    C2C createC2C();

    Convert createConvert();

    CryptoLoans createCryptoLoans();

    Fiat createFiat();

    Futures createFutures();

    GiftCard createGiftCard();

    Market createMarket();

    Margin createMargin();

    Mining createMining();

    NFT createNFT();

    Pay createPay();

    PortfolioMargin createPortfolioMargin();

    Rebate createRebate();

    Savings createSavings();

    Staking createStaking();

    SubAccount createSubAccount();

    Trade createTrade();

    UserData createUserData();

    Wallet createWallet();
}
