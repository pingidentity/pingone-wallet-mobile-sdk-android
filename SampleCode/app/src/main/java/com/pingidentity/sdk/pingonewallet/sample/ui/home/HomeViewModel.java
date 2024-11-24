package com.pingidentity.sdk.pingonewallet.sample.ui.home;


import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.pingidentity.did.sdk.types.Claim;
import com.pingidentity.sdk.pingonewallet.sample.models.Credential;
import com.pingidentity.sdk.pingonewallet.sample.ui.base.BaseViewModel;
import com.pingidentity.sdk.pingonewallet.sample.wallet.PingOneWalletHelper;

import java.util.List;

public class HomeViewModel extends BaseViewModel {

    public HomeViewModel(PingOneWalletHelper pingOneWalletHelper) {
        super(pingOneWalletHelper);
    }

    public MutableLiveData<List<Claim>> getClaims() {
        return getDataManager().subscribeCredentialsChange();
    }

    public boolean isClaimRevoked(@NonNull String claimId) {
        return getDataManager().isCredentialRevoked(claimId);
    }

    public void goToQrScannerClick() {
        navigate(HomeFragmentDirections.actionHomeFragmentToQrScannerFragment());
    }

    public void goToDetails(Credential credential) {
        navigate(HomeFragmentDirections.actionHomeFragmentToCredentialDetailsFragment(credential));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        getPingOneWalletHelper().stopPolling();
    }
}
