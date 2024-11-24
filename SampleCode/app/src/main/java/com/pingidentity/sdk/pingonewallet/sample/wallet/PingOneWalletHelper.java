package com.pingidentity.sdk.pingonewallet.sample.wallet;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.fragment.app.FragmentActivity;

import com.pingidentity.did.sdk.client.service.NotFoundException;
import com.pingidentity.did.sdk.client.service.model.ApplicationInstance;
import com.pingidentity.did.sdk.client.service.model.Challenge;
import com.pingidentity.did.sdk.types.Claim;
import com.pingidentity.did.sdk.types.ClaimReference;
import com.pingidentity.did.sdk.types.Share;
import com.pingidentity.did.sdk.w3c.verifiableCredential.OpenUriAction;
import com.pingidentity.did.sdk.w3c.verifiableCredential.PresentationAction;
import com.pingidentity.sdk.pingonewallet.client.PingOneWalletClient;
import com.pingidentity.sdk.pingonewallet.contracts.WalletCallbackHandler;
import com.pingidentity.sdk.pingonewallet.errors.WalletException;
import com.pingidentity.sdk.pingonewallet.sample.R;
import com.pingidentity.sdk.pingonewallet.sample.wallet.interfaces.ApplicationUiHandler;
import com.pingidentity.sdk.pingonewallet.sample.wallet.interfaces.CredentialPicker;
import com.pingidentity.sdk.pingonewallet.storage.data_repository.DataRepository;
import com.pingidentity.sdk.pingonewallet.types.CredentialMatcherResult;
import com.pingidentity.sdk.pingonewallet.types.CredentialsPresentation;
import com.pingidentity.sdk.pingonewallet.types.PairingRequest;
import com.pingidentity.sdk.pingonewallet.types.PresentationRequest;
import com.pingidentity.sdk.pingonewallet.types.WalletEvents.WalletCredentialEvent;
import com.pingidentity.sdk.pingonewallet.types.WalletEvents.WalletError;
import com.pingidentity.sdk.pingonewallet.types.WalletEvents.WalletEvent;
import com.pingidentity.sdk.pingonewallet.types.WalletEvents.WalletPairingEvent;
import com.pingidentity.sdk.pingonewallet.types.WalletMessage.credential.CredentialAction;
import com.pingidentity.sdk.pingonewallet.types.regions.PingOneRegion;
import com.pingidentity.sdk.pingonewallet.utils.BackgroundThreadHandler;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * @noinspection ResultOfMethodCallIgnored
 */
@SuppressLint({"CheckResult", "ApplySharedPref"})
public class PingOneWalletHelper implements WalletCallbackHandler {

    public static final String TAG = PingOneWalletHelper.class.getCanonicalName();
    public static final String PUSH_DISABLED_KEY = "push_disabled";
    public static final String POLLING_ENABLED_KEY = "polling_enabled";

    private final PingOneWalletClient pingOneWalletClient;
    private final WeakReference<FragmentActivity> contextWeakReference;
    private final SharedPreferences preferences;
    /**
     * Set this to true if push notifications are not configured in your app
     */

    public boolean enablePolling;
    private ApplicationUiHandler applicationUiHandler;
    private CredentialPicker credentialPicker;

    @SuppressLint("CheckResult")
    private PingOneWalletHelper(PingOneWalletClient client, FragmentActivity context) {
        this.pingOneWalletClient = client;
        this.contextWeakReference = new WeakReference<>(context);
        this.preferences = context.getSharedPreferences("polling_preference", MODE_PRIVATE);
        this.enablePolling = preferences.getBoolean(POLLING_ENABLED_KEY, false);
        client.registerCallbackHandler(this);
        BackgroundThreadHandler.singleBackgroundThreadHandler().post(() -> getPushToken(pushToken -> {
            if (pushToken != null) {
                updatePushToken(pushToken);
            }
        }));

        this.pingOneWalletClient.checkForMessages();
    }

    /**
     * This method erases all the credentials and key pairs associated with the wallet and resets it to a clean state. The data is not recoverable after this method is executed.
     * Must call {@link #initializeWallet(FragmentActivity, Consumer, Consumer)} to use the wallet again.
     *
     * @param context Application context
     */
    public static void resetWallet(@NonNull Context context) {
        PingOneWalletClient.reset(context);
    }

    public static void initializeWallet(FragmentActivity context, Consumer<PingOneWalletHelper> onResult, Consumer<Throwable> onError) {
        PingOneRegion defaultRegion = PingOneRegion.NA;
        Completable.fromRunnable(() -> new PingOneWalletClient.Builder(context, defaultRegion)
                        .useDefaultStorage(context)
                        .build(pingOneWalletClient -> {
                            PingOneWalletHelper helper = new PingOneWalletHelper(pingOneWalletClient, context);
                            onResult.accept(helper);
                        }, onError))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    /**
     * Set optional ApplicationUiHandler to handle UI notifications/Alerts etc.
     *
     * @param applicationUiHandler: Implementation of interface ApplicationUiHandler
     * @see ApplicationUiHandler
     */
    public void setApplicationUiHandler(ApplicationUiHandler applicationUiHandler) {
        this.applicationUiHandler = applicationUiHandler;
    }

    /**
     * Set optional CredentialPicker implementation to handle credential selection when multiple credentials of same type are present in the wallet matching the criteria in the presentation request.
     *
     * @param credentialPicker: Implementation of interface CredentialPicker
     * @see CredentialPicker
     */
    public void setCredentialPicker(CredentialPicker credentialPicker) {
        this.credentialPicker = credentialPicker;
    }

    /**
     * This method returns the data repository used by the wallet for storing ApplicationInstances and Credentials. See DataRepository for more details.
     *
     * @return DataRepository used by Wallet Instance
     * @see DataRepository
     */
    public DataRepository getDataRepository() {
        return pingOneWalletClient.getDataRepository();
    }

    /**
     * Call this method to process PingOne Credentials QR codes and Universal links.
     *
     * @param qrContent: Content of the scanned QR code or Universal link used to open the app
     */
    public void processPingOneRequest(@NonNull String qrContent) {
        pingOneWalletClient.processPingOneRequest(qrContent);
    }

    /**
     * Call this method when a credential is deleted from the Wallet. Reporting this action will help admins view accurate stats on their dashboards in future.
     *
     * @param claim: Deleted credential
     */
    public void reportCredentialDeletion(@NonNull Claim claim) {
        pingOneWalletClient.reportCredentialDeletion(claim);
    }

    /**
     * Call this method to check if wallet has received any new messages in the mailbox.
     * This method can be used to check for messages on user action or if push notifications are not available.
     */
    public void checkForMessages() {
        pingOneWalletClient.checkForMessages();
    }

    /**
     * Call this method to start polling for new messages sent to the wallet. Use this method only if you are not using push notifications.
     */
    public void pollForMessages() {
        this.enablePolling = true;
        preferences.edit().putBoolean(POLLING_ENABLED_KEY, true).commit();
        pingOneWalletClient.pollForMessages();
    }

    /**
     * Call this method to stop polling for messages sent to the wallet.
     */
    public void stopPolling() {
        this.enablePolling = false;
        preferences.edit().putBoolean(POLLING_ENABLED_KEY, false).commit();
        pingOneWalletClient.stopPolling();
    }

    /**
     * Returns boolean indicating if Wallet SDK should poll for messages
     *
     * @return Boolean
     */
    public boolean isPollingEnabled() {
        return this.enablePolling;
    }

    /**
     * Returns ApplicationInstance objects mapped to PingOneRegion
     *
     * @return Map of application instances registered by the SDK for different PingOne regions
     */
    public Map<PingOneRegion, ApplicationInstance> getAllApplicationInstances() {
        Map<PingOneRegion, ApplicationInstance> result = new HashMap<>();
        pingOneWalletClient.getDataRepository().getRegions().forEach(pingOneRegion -> {
            ApplicationInstance instance = pingOneWalletClient.getDataRepository().getApplicationInstance(pingOneRegion);
            if (instance != null) {
                result.put(pingOneRegion, instance);
            }
        });
        return result;
    }

    /**
     * Set the push token for the device to be able to receive push notifications.
     *
     * @param pushToken for the app
     */
    public void updatePushToken(String pushToken) {
        this.pingOneWalletClient.updatePushNotificationToken(pushToken)
                .subscribe(() -> Log.d(TAG, "Push updated successfully"),
                        throwable -> Log.e(TAG, "Error updating push", throwable));
    }

    /**
     * Returns whether user has disabled push notifications
     *
     * @return Boolean
     */
    public boolean isPushDisabled() {
        return preferences.getBoolean(PUSH_DISABLED_KEY, false);
    }

    /**
     * This method enables push notifications for the app and updates the token with backend if it is not disabled in settings
     */
    public void enablePush() {
        preferences.edit().putBoolean(PUSH_DISABLED_KEY, false).commit();
        getPushToken(pushToken -> {
            if (pushToken != null) {
                updatePushToken(pushToken);
            }
        });
    }

    /**
     * This method disables push notifications for the app and notifies backend about the change
     */
    public void disablePush() {
        preferences.edit().putBoolean(PUSH_DISABLED_KEY, true).commit();
        this.pingOneWalletClient.disablePush()
                .subscribe(() -> Log.d(TAG, "Push updated successfully"),
                        throwable -> Log.e(TAG, "Error updating push", throwable));
    }

    /**
     * Delete all the credentials from storage
     */
    public void deleteAllCreds() {
        this.pingOneWalletClient.getDataRepository().getAllCredentials()
                .forEach(claim -> pingOneWalletClient.getDataRepository().deleteCredential(claim.getId().toString()));
    }

    /////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////// WalletCallbackHandler Implementation /////////////////////
    /////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handle the newly issued credential.
     *
     * @param issuer:    ApplicationInstanceID of the credential issuer
     * @param message:   Optional string message
     * @param challenge: Optional challenge
     * @param claim:     Issued credential
     * @param errors:    List of any errors while processing/verifying the credential
     * @return boolean: True if the user has accepted the credential, False if the user has rejected the credential
     */
    @Override
    public boolean handleCredentialIssuance(String issuer, String message, Challenge challenge, Claim claim, List<WalletException> errors) {
        Log.i(TAG, "handleCredentialIssuance");
        Log.i(TAG, "Credential received: Issuer: " + issuer + " message: " + message);
        pingOneWalletClient.getDataRepository().saveCredential(claim);
        BackgroundThreadHandler.postOnMainThread(() -> notifyUser("Received a new credential"));
        return true;
    }

    /**
     * Handle the revocation of a credential.
     *
     * @param issuer:         ApplicationInstanceID of the credential issuer
     * @param message:        Optional string message
     * @param challenge:      Optional challenge
     * @param claimReference: ClaimReference for the revoked credential
     * @param errors:         List of any errors while revoking the credential
     * @return True if the user has accepted the credential revocation, False if the user has rejected the credential revocation
     */
    @Override
    public boolean handleCredentialRevocation(String issuer, String message, Challenge challenge, ClaimReference claimReference, List<WalletException> errors) {
        Log.i(TAG, "handleCredentialRevocation");
        Log.i(TAG, "Credential revoked: Issuer: " + issuer + " message: " + message);
        pingOneWalletClient.getDataRepository().saveCredentialReference(claimReference);
        BackgroundThreadHandler.postOnMainThread(() -> notifyUser("Credential Revoked"));
        return true;
    }

    /**
     * This callback is triggered when another wallet shares a credential with the current application instance.
     *
     * @param sender:    ApplicationInstanceID of the sender
     * @param message:   Optional string message
     * @param challenge: Optional challenge
     * @param claim:     Shared credential
     * @param errors:    List of any errors while verifying the shared credential
     */
    @Override
    public void handleCredentialPresentation(String sender, String message, Challenge challenge, List<Share> claim, List<WalletException> errors) {
        Log.i(TAG, "handleCredentialPresentation");
        BackgroundThreadHandler.postOnMainThread(() -> notifyUser("Coming soon..."));
    }

    /**
     * This callback is triggered when a credential is requested from the current wallet using supported protocols.
     *
     * @param presentationRequest PresentationRequest for presenting Credentials from wallet
     */
    @Override
    public void handleCredentialRequest(PresentationRequest presentationRequest) {
        if (presentationRequest.isPairingRequest()) {
            handlePairingRequest(presentationRequest);
            return;
        }

        BackgroundThreadHandler.postOnMainThread(() -> notifyUser("Processing presentation request..."));

        List<Claim> allClaims = pingOneWalletClient.getDataRepository().getAllCredentials();
        List<CredentialMatcherResult> credentialMatcherResults = pingOneWalletClient.findMatchingCredentialsForRequest(presentationRequest, allClaims).getResult();
        List<CredentialMatcherResult> matchingCredentials = Collections.emptyList();
        if (credentialMatcherResults != null) {
            matchingCredentials = credentialMatcherResults.stream().filter(result -> !result.getClaims().isEmpty()).collect(Collectors.toList());
        }
        if (matchingCredentials.isEmpty()) {
            showError(R.string.dialog_no_matching_cred_title, R.string.dialog_no_matching_cred_message);
            return;
        }
        if (credentialPicker == null) {
            return;
        }

        int message = matchingCredentials.size() == credentialMatcherResults.size() ? R.string.dialog_presentation_message : R.string.dialog_presentation_message_missing_credential;
        int title = matchingCredentials.size() == credentialMatcherResults.size() ? R.string.dialog_presentation_title : R.string.dialog_presentation_title_missing_credential;
        askUserPermission(title, message, isPositiveAction -> {
            if (isPositiveAction) {
                selectCredential(presentationRequest, credentialMatcherResults);
            } else {
                Log.i(TAG, "Presentation rejected by user.");
                notifyUser("Presentation canceled");
            }
        });
    }

    private void selectCredential(PresentationRequest presentationRequest, List<CredentialMatcherResult> credentialMatcherResults) {
        credentialPicker.selectCredentialFor(presentationRequest, credentialMatcherResults, result -> {
            if (result == null || result.isEmpty()) {
                notifyUser("Presentation canceled");
                return;
            }
            shareCredentialPresentation(result);
        });
    }

    @Override
    public void handleError(WalletException error) {
        Log.i(TAG, "handleError");
        Log.e(TAG, "Exception in message processing", error);
        if (error.getCause() instanceof NotFoundException) {
            notifyUser("Failed to process request");
        }
    }

    /**
     * Callback returns different events when using Wallet, including errors
     * Backward compatibility - Call handleEvent() if you're still using `handleError` callback to manage exceptions
     *
     * @param event: WalletEvent
     */
    @Override
    public void handleEvent(WalletEvent event) {
        switch (event.getType()) {
            case PAIRING:
                handlePairingEvent((WalletPairingEvent) event);
                break;
            case CREDENTIAL_UPDATE:
                handleCredentialEvent((WalletCredentialEvent) event);
                break;
            case ERROR:
                handleErrorEvent((WalletError) event);
                break;
            default:
                Log.e(TAG, "Received unknown event: " + event.getType());
        }

    }

    private void handlePairingRequest(PairingRequest pairingRequest) {
        BackgroundThreadHandler.singleBackgroundThreadHandler().post(() ->
                getPushToken(pushToken -> askUserPermission(R.string.dialog_pairing_title, R.string.dialog_pairing_message, isPositiveAction -> {
                    if (isPositiveAction) {
                        try {
                            pingOneWalletClient.pairWallet(pairingRequest, pushToken);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to pair wallet", e);
                            notifyUser("Wallet pairing failed");
                        }
                    } else {
                        notifyUser("Pairing canceled");
                        Log.i(TAG, "User rejected pairing request");
                    }
                })));
    }

    private void handlePairingRequest(@NonNull PresentationRequest presentationRequest) {
        PairingRequest pairingRequest = presentationRequest.getPairingRequest();
        if (pairingRequest != null) {
            handlePairingRequest(pairingRequest);
        } else {
            Log.e(TAG, "Wallet pairing failed: Invalid request for pairing");
            notifyUser("Wallet pairing failed");
        }

    }

    private void shareCredentialPresentation(@NonNull CredentialsPresentation credentialsPresentation) {
        presentCredential(credentialsPresentation);
    }

    /**
     * @noinspection ResultOfMethodCallIgnored
     */
    private void presentCredential(@NonNull CredentialsPresentation credentialsPresentation) {
        BackgroundThreadHandler.singleBackgroundThreadHandler().post(() ->
                pingOneWalletClient.presentCredentials(credentialsPresentation)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(presentationResult -> {
                            switch (presentationResult.getPresentationStatus().getStatus()) {
                                case SUCCESS:
                                    notifyUser("Information sent successfully");
                                    break;
                                case FAILURE:
                                    notifyUser("Failed to present credential");
                                    if (presentationResult.getError() != null) {
                                        Log.e(TAG, "\"Error sharing information: ", presentationResult.getError());
                                    }
                                    Log.e(TAG, String.format("Presentation failed. %s", presentationResult.getDetails()));
                                    break;
                                case REQUIRES_ACTION:
                                    handlePresentationAction(presentationResult.getPresentationStatus().getAction());
                            }
                        }));
    }

    /**
     * @noinspection SwitchStatementWithTooFewBranches
     */
    private void handlePresentationAction(PresentationAction action) {
        if (action == null) {
            return;
        }
        switch (action.getActionType()) {
            case OPEN_URI:
                OpenUriAction openUriAction = (OpenUriAction) action;
                String appOpenUri = openUriAction.getRedirectUri();
                if (applicationUiHandler != null) {
                    applicationUiHandler.openUri(appOpenUri);
                } else {
                    Log.e(TAG, "Must implement ApplicationUiHandler for this");
                }
        }
    }

    private void handlePairingEvent(WalletPairingEvent event) {
        Log.i(TAG, "Wallet paired: " + event.isSuccess());
        switch (event.getPairingEventType()) {
            case PAIRING_REQUEST:
                handlePairingRequest(event.getPairingRequest());
                break;
            case PAIRING_RESPONSE:
                if (event.isSuccess()) {
                    this.notifyUser("Wallet paired successfully");
                } else {
                    this.notifyUser("Wallet pairing failed");
                    if (event.getError() != null) {
                        Log.e(TAG, "Wallet Pairing Error", event.getError());
                    }
                }
        }
    }

    private void handleErrorEvent(WalletError errorEvent) {
        Log.e(TAG, "Error in wallet callback handler", errorEvent.getError());
    }

    /**
     * @noinspection SwitchStatementWithTooFewBranches
     */
    private void handleCredentialEvent(WalletCredentialEvent event) {
        switch (event.getCredentialEvent()) {
            case CREDENTIAL_UPDATED:
                handleCredentialUpdate(event.getAction(), event.getReferenceCredentialId());
        }
    }

    /**
     * @noinspection SwitchStatementWithTooFewBranches
     */
    private void handleCredentialUpdate(CredentialAction action, String referenceCredentialId) {
        switch (action) {
            case DELETE:
                pingOneWalletClient.getDataRepository().deleteCredential(referenceCredentialId);
        }
    }

    public void observePushNotifications(@NonNull FragmentActivity context) {
        if (applicationUiHandler == null || applicationUiHandler.getNotificationServiceHelper() == null) {
            return;
        }

        applicationUiHandler.getNotificationServiceHelper().getNotificationData().observe(context, notificationData -> {
            if (notificationData != null) {
                pingOneWalletClient.processNotification(new HashMap<>(notificationData));
                applicationUiHandler.getNotificationServiceHelper().clearNotificationData();
            }
        });
    }

    public void getPushToken(@NonNull Consumer<String> resultConsumer) {
        if (applicationUiHandler == null || applicationUiHandler.getNotificationServiceHelper() == null) {
            resultConsumer.accept(null);
            return;
        }

        if (isPushDisabled()) {
            resultConsumer.accept("");
            return;
        }

        String pushToken = applicationUiHandler.getNotificationServiceHelper().getPushToken().getValue();

        if (pushToken == null && contextWeakReference.get() != null) {
            BackgroundThreadHandler.postOnMainThread(() -> applicationUiHandler.getNotificationServiceHelper().getPushToken().observe(contextWeakReference.get(),
                    token -> BackgroundThreadHandler.singleBackgroundThreadHandler().post(() -> resultConsumer.accept(token))));

        } else {
            resultConsumer.accept(pushToken);
        }
    }


    // ApplicationUIHandler Calls
    private void notifyUser(@NonNull String message) {
        if (applicationUiHandler == null) {
            return;
        }
        applicationUiHandler.showToast(message);
    }

    private void askUserPermission(int title, int message, @NonNull java.util.function.Consumer<Boolean> consumer) {
        if (applicationUiHandler == null) {
            consumer.accept(true);
            return;
        }

        applicationUiHandler.showConfirmationAlert(title, message, consumer);
    }

    /**
     * @noinspection SameParameterValue
     */
    private void showError(int title, int message) {
        if (applicationUiHandler == null) {
            return;
        }
        applicationUiHandler.showAlert(title, message);
    }

}
