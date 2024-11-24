package com.pingidentity.sdk.pingonewallet.sample.ui.home;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.pingidentity.sdk.pingonewallet.sample.MainApplication;
import com.pingidentity.sdk.pingonewallet.sample.databinding.FragmentHomeBinding;
import com.pingidentity.sdk.pingonewallet.sample.di.component.FragmentComponent;
import com.pingidentity.sdk.pingonewallet.sample.models.Credential;
import com.pingidentity.sdk.pingonewallet.sample.rv_adapters.CardsListAdapter;
import com.pingidentity.sdk.pingonewallet.sample.ui.base.BaseFragment;
import com.pingidentity.sdk.pingonewallet.utils.DeviceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HomeFragment extends BaseFragment<FragmentHomeBinding, HomeViewModel> {

    private final List<Credential> mDocuments = new ArrayList<>();

    private final Observer<String> appOpenUrlObserver = url -> {
        if (url != null) {
            mViewModel.processUrl(url);
            ((MainApplication) requireActivity().getApplication()).setUrl(null);
        }
    };

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setCardsListAdapter();
        processAppOpenUrl();
        retrieveData();
        setupClickListeners();
        setupRefreshListener();
    }

    @Override
    public void onResume() {
        super.onResume();
        mViewModel.getPingOneWalletHelper().checkForMessages();
    }

    private void setupRefreshListener() {
        SwipeRefreshLayout swipeRefreshLayout = getViewBinding().swipeContainer;
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (mViewModel.getPingOneWalletHelper().isPollingEnabled()) {
                if (mViewModel.getPingOneWalletHelper().getDataRepository().getAllCredentials().isEmpty()) {
                    mViewModel.getPingOneWalletHelper().pollForMessages();
                } else {
                    mViewModel.getPingOneWalletHelper().stopPolling();
                }
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 3000);
        });
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    @Override
    public FragmentHomeBinding performBinding(@NonNull LayoutInflater inflater, ViewGroup container) {
        return FragmentHomeBinding.inflate(inflater, container, false);
    }

    @Override
    public void performDependencyInjection(FragmentComponent buildComponent) {
        buildComponent.inject(this);
    }

    private void setupClickListeners() {
        getViewBinding().btnScanQr.setOnClickListener(view -> {
            if (DeviceUtils.isRunningOnEmulator(requireContext())) {
                showTextboxAlert();
                Toast.makeText(requireContext(), "Running on emulator", Toast.LENGTH_SHORT).show();
            } else {
                mViewModel.goToQrScannerClick();
            }
        });
    }

    private void showTextboxAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Copy the URL for execution");
        builder.setMessage("");

        EditText input = new EditText(requireContext());
        input.setHint("Enter the URL");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Submit", null);

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String inputText = input.getText().toString().trim();
                    if (TextUtils.isEmpty(inputText)) {
                        input.setError("Input cannot be empty!");
                    } else {
                        dialog.dismiss();
                        mViewModel.processUrl(inputText);
                    }
                }));
        dialog.show();

    }

    private void processAppOpenUrl() {
        ((MainApplication) requireActivity().getApplication()).getUrl().observe(getViewLifecycleOwner(), appOpenUrlObserver);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void retrieveData() {
        mViewModel.getClaims().observe(getViewLifecycleOwner(), claims -> {
            if (claims == null) {
                return;
            }
            mDocuments.clear();
            mDocuments.addAll(claims.stream()
                    .map(claim -> new Credential(claim, mViewModel.isClaimRevoked(claim.getId().toString())))
                    .collect(Collectors.toList()));
            getViewBinding().rvCredentials.getAdapter().notifyDataSetChanged();
        });
    }

    private void setCardsListAdapter() {
        CardsListAdapter adapter = new CardsListAdapter(mDocuments, credential -> mViewModel.goToDetails(credential));
        getViewBinding().rvCredentials.setLayoutManager(new LinearLayoutManager(requireContext()));
        getViewBinding().rvCredentials.setAdapter(adapter);
    }

}
