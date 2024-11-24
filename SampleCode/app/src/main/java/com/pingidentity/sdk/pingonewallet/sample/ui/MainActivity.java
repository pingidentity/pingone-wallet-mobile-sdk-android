package com.pingidentity.sdk.pingonewallet.sample.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import com.pingidentity.sdk.pingonewallet.errors.BiometricException;
import com.pingidentity.sdk.pingonewallet.sample.MainApplication;
import com.pingidentity.sdk.pingonewallet.sample.R;
import com.pingidentity.sdk.pingonewallet.sample.databinding.ActivityMainBinding;
import com.pingidentity.sdk.pingonewallet.sample.ui.home.HomeFragment;
import com.pingidentity.sdk.pingonewallet.sample.ui.picker.default_impl.DefaultCredentialPicker;
import com.pingidentity.sdk.pingonewallet.sample.utils.AppInfoUtils;
import com.pingidentity.sdk.pingonewallet.sample.wallet.PingOneWalletHelper;
import com.pingidentity.sdk.pingonewallet.sample.wallet.interfaces.ApplicationUiHandler;
import com.pingidentity.sdk.pingonewallet.utils.BackgroundThreadHandler;

import java.security.UnrecoverableKeyException;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getCanonicalName();
    private final ApplicationUiHandler applicationUiHandler = ApplicationUiHandlerImpl.getInstance(this);
    private PingOneWalletHelper pingOneWalletHelper;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // FCM SDK (and your app) can post notifications.
                    pingOneWalletHelper.getPushToken(new Consumer<String>() {
                        @Override
                        public void accept(String pushToken) {
                            if (!pingOneWalletHelper.isPushDisabled()) {
                                pingOneWalletHelper.updatePushToken(pushToken);
                            }
                        }
                    });
                } else {
                    pingOneWalletHelper.disablePush();
                    // TODO: Inform user that that your app will not show notifications.
                }
            });
    private View errorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.pingidentity.sdk.pingonewallet.sample.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }
        errorView = findViewById(R.id.error_init_wallet_layout);
        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Erasing wallet data...", Toast.LENGTH_LONG).show();
                PingOneWalletHelper.resetWallet(MainActivity.this);
                initWallet();
            }
        });
        initWallet();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int pushPermissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS);
            if (pushPermissionStatus == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Push notification permission granted");
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display a message explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission.
                Log.d(TAG, "User requires further rationale to allow push notification permission., disabling push for now");
                pingOneWalletHelper.disablePush();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem infoItem = menu.findItem(R.id.action_info);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (navHost != null) {
            // Check if the current fragment is HomeFragment
            Fragment currentFragment = navHost.getChildFragmentManager().getPrimaryNavigationFragment();
            infoItem.setVisible(currentFragment instanceof HomeFragment);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_info) {
            // Handle the info icon click
            String message = AppInfoUtils.getAppInfoMessageFor(this.pingOneWalletHelper.getAllApplicationInstances(), this);
            showCustomAlert(message);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initWallet() {
        PingOneWalletHelper.initializeWallet(this, helper -> {
            this.pingOneWalletHelper = helper;
            setupDependencyInjection(helper);
            BackgroundThreadHandler.postOnMainThread(() -> {
                setupNavigation();
                askNotificationPermission();
                helper.observePushNotifications(MainActivity.this);
            });
        }, throwable -> {
            if (throwable.getCause() instanceof BiometricException) {
                errorView.setVisibility(View.GONE);
                showPasswordDialog();
            } else if (throwable instanceof UnrecoverableKeyException) {
                Log.e(TAG, "Cannot init wallet", throwable);
                errorView.setVisibility(View.VISIBLE);
            } else {
                errorView.setVisibility(View.GONE);
                Log.e(TAG, "Failed to init wallet", throwable);
                applicationUiHandler.showAlert(R.string.error, R.string.wallet_init_error);
            }
        });
    }

    private void setupDependencyInjection(PingOneWalletHelper pingOneWalletHelper) {
        ((MainApplication) getApplication()).initDagger(pingOneWalletHelper);
        pingOneWalletHelper.setApplicationUiHandler(applicationUiHandler);
        pingOneWalletHelper.setCredentialPicker(new DefaultCredentialPicker(applicationUiHandler));
    }

    private void setupNavigation() {
        errorView.setVisibility(View.GONE);
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (navHost == null) {
            return;
        }
        NavController navController = navHost.getNavController();
        NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
        graph.setStartDestination(R.id.homeFragment);
        navController.setGraph(graph);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            invalidateOptionsMenu(); // Refresh the menu on fragment change
        });
    }

    private void showPasswordDialog() {
        BackgroundThreadHandler.postOnMainThread(() -> new AlertDialog.Builder(this)
                .setMessage("For security purposes, the application stores data in encrypted storage and requires established biometric data")
                .setPositiveButton("Setup biometric data", (dialog, which) -> {
                    openPasswordSettings();
                    dialog.dismiss();
                })
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show());
    }

    private void openPasswordSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(new Intent(Settings.ACTION_BIOMETRIC_ENROLL));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                startActivity(new Intent(Settings.ACTION_FINGERPRINT_ENROLL));
            } else {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleInterceptedIntent(intent);
    }

    private void handleInterceptedIntent(@NonNull Intent intent) {
        MainApplication application = (MainApplication) getApplication();
        if (intent.getData() != null) {
            Log.i(TAG, String.format("Intent intercepted: %s", intent.getData().toString()));
            application.setUrl(intent.getData().toString());
        }
    }

    //// Custom dialog for easter eggs

    private void showCustomAlert(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Application Info");

        TextView bodyTextView = new TextView(this);
        bodyTextView.setText(message);
        bodyTextView.setTextSize(14);
        bodyTextView.setPadding(30, 20, 30, 20); // Set padding for better appearance
        bodyTextView.setTextColor(Color.BLACK);
        builder.setView(bodyTextView);
        builder.setPositiveButton("OK", null); // Optional button for the dialog

        builder.setNeutralButton("Copy to Clipboard", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("copiedText", message);
            clipboard.setPrimaryClip(clip);
            applicationUiHandler.showToast("Message copied to clipboard");
        });

        AlertDialog dialog = builder.create();

        int[] clickCount = {0};
        bodyTextView.setOnClickListener(v -> {
            clickCount[0]++;
            if (clickCount[0] >= 4) {
                dialog.dismiss();
                showActionAlert();
            }
        });


        dialog.show();
    }

    private void showActionAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter the command");
        builder.setMessage("start polling, stop polling, enable push, disable push, delete creds");

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Submit", (dialog, which) -> {
            // Get the input from the EditText
            String userInput = input.getText().toString();
            handleUserAction(userInput.trim());
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void handleUserAction(String action) {
        switch (action) {
            case AppInfoUtils.START_POLLING:
                this.pingOneWalletHelper.pollForMessages();
                applicationUiHandler.showToast("Polling started");
                break;
            case AppInfoUtils.STOP_POLLING:
                this.pingOneWalletHelper.stopPolling();
                applicationUiHandler.showToast("Polling stopped");
                break;
            case AppInfoUtils.ENABLE_PUSH:
                this.pingOneWalletHelper.enablePush();
                applicationUiHandler.showToast("Push enabled");
                break;
            case AppInfoUtils.DISABLE_PUSH:
                this.pingOneWalletHelper.disablePush();
                applicationUiHandler.showToast("Push disabled");
                break;
            case AppInfoUtils.DELETE_CREDS:
                this.pingOneWalletHelper.deleteAllCreds();
                applicationUiHandler.showToast("Deleting all credentials");
                break;
            default:
                Log.d(TAG, String.format("Not handling %s yet", action));
                applicationUiHandler.showToast("Invalid command");
        }
    }

}
