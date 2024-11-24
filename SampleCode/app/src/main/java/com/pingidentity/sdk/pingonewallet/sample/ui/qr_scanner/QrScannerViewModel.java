package com.pingidentity.sdk.pingonewallet.sample.ui.qr_scanner;

import com.pingidentity.sdk.pingonewallet.sample.ui.base.BaseViewModel;
import com.pingidentity.sdk.pingonewallet.sample.wallet.PingOneWalletHelper;

public class QrScannerViewModel extends BaseViewModel {

    public QrScannerViewModel(PingOneWalletHelper helper) {
        super(helper);
    }

    private void goBack() {
        navigateBack();
    }

}
