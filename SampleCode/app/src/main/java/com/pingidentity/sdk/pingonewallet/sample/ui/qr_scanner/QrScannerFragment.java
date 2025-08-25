package com.pingidentity.sdk.pingonewallet.sample.ui.qr_scanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.navigation.Navigation;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.pingidentity.sdk.pingonewallet.sample.R;
import com.pingidentity.sdk.pingonewallet.sample.databinding.FragmentQrScannerBinding;
import com.pingidentity.sdk.pingonewallet.sample.di.component.FragmentComponent;
import com.pingidentity.sdk.pingonewallet.sample.ui.base.BaseFragment;
import com.pingidentity.sdk.pingonewallet.utils.BackgroundThreadHandler;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class QrScannerFragment extends BaseFragment<FragmentQrScannerBinding, QrScannerViewModel> {

    public static final String TAG = QrScannerFragment.class.getCanonicalName();

    private static final float FRAME_PADDING = 10f;

    private float scaleX = 1f;
    private float scaleY = 1f;

    private ListenableFuture<ProcessCameraProvider> mCameraProviderFuture;
    private final ActivityResultLauncher<String> permissionResult =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
                if (Boolean.TRUE.equals(result)) {
                    bindCameraUseCases();
                } else {
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            });
    private BarcodeBoxView mBarcodeBoxView;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBarcodeBoxView = new BarcodeBoxView(requireContext());
        getViewBinding().viewContainer.addView(mBarcodeBoxView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        checkPermission();
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                mViewModel.navigateBack();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);
    }

    @Override
    public FragmentQrScannerBinding performBinding(@NonNull LayoutInflater inflater, ViewGroup container) {
        return FragmentQrScannerBinding.inflate(inflater, container, false);
    }

    @Override
    public void performDependencyInjection(FragmentComponent buildComponent) {
        buildComponent.inject(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            mCameraProviderFuture.get().unbindAll();
        } catch (ExecutionException e) {
            Log.e(TAG, "Failed to unbind camera.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Action interrupted.", e);
        }
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCameraUseCases();
        } else {
            permissionResult.launch(Manifest.permission.CAMERA);
        }
    }

    //////////////////////////////////////////
    ///////// Camera Works ///////////////////
    //////////////////////////////////////////

    private void bindCameraUseCases() {
        mCameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity());
        mCameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = mCameraProviderFuture.get();
                Preview previewUseCase = new Preview.Builder().build();
                ImageAnalysis analysisUseCase = getAnalysisUseCase();
                previewUseCase.setSurfaceProvider(getViewBinding().previewView.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.bindToLifecycle(QrScannerFragment.this, cameraSelector, previewUseCase, analysisUseCase);
            } catch (ExecutionException e) {
                Log.e(TAG, "Failed to bind camera", e);
                showError("Cannot start camera. Please try again.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Action interrupted.", e);
                showError("Cannot start camera. Please try again.");
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private ImageAnalysis getAnalysisUseCase() {
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
        BarcodeScanner scanner = BarcodeScanning.getClient(options);
        ImageAnalysis analysisUseCase = new ImageAnalysis.Builder().build();
        analysisUseCase.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                imageProxy -> processImageProxy(scanner, imageProxy, analysisUseCase)
        );
        return analysisUseCase;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processImageProxy(BarcodeScanner barcodeScanner, ImageProxy imageProxy, ImageAnalysis analysisUseCase) {
        if (imageProxy.getImage() == null) {
            return;
        }
        Image image = imageProxy.getImage();
        InputImage inputImage = InputImage.fromMediaImage(image, imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> processBarcode(barcodes, analysisUseCase, inputImage))
                .addOnFailureListener(throwable -> {
                    Log.e(TAG, "Failed to process barcode.", throwable);
                    showError("Unable to process barcode. Please scan a valid code.");
                })
                .addOnCompleteListener(task -> {
                    imageProxy.getImage().close();
                    imageProxy.close();
                });
    }

    //////////////////////////////////////////
    ///////// Process Barcode ////////////////
    //////////////////////////////////////////

    private void processBarcode(List<Barcode> barcodes, ImageAnalysis analysisUseCase, InputImage inputImage) {
        if (barcodes == null || barcodes.isEmpty()) {
            mBarcodeBoxView.setRect(new RectF());
            return;
        }
        Barcode barcode = barcodes.get(0);
        if (barcode != null && barcode.getRawValue() != null) {
            scaleX = getViewBinding().previewView.getWidth() / (float) inputImage.getHeight();
            scaleY = getViewBinding().previewView.getHeight() / (float) inputImage.getWidth();
            mBarcodeBoxView.setRect(adjustBoundingRect(Objects.requireNonNull(barcode.getBoundingBox())));
            try {
                mCameraProviderFuture.get().unbind(analysisUseCase);
                stepDelay(() -> {
                    mViewModel.processUrl(barcode.getRawValue());
                    Navigation.findNavController(getViewBinding().getRoot()).navigateUp();
                });
            } catch (ExecutionException e) {
                Log.e(TAG, "Failed to bind camera", e);
                showError("Cannot start camera. Please try again.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Action interrupted.", e);
                showError("Cannot start camera. Please try again.");
            }
        }
    }

    //////////////////////////////////////////
    ///////// QR Code frame //////////////////
    //////////////////////////////////////////

    private RectF adjustBoundingRect(Rect rect) {
        return new RectF(
                translateX(rect.left - FRAME_PADDING),
                translateY(rect.top - FRAME_PADDING),
                translateX(rect.right + FRAME_PADDING),
                translateY(rect.bottom + FRAME_PADDING)
        );
    }

    private Float translateX(Float x) {
        return x * scaleX;
    }

    private Float translateY(Float y) {
        return y * scaleY;
    }

    private void stepDelay(Runnable runnable) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                requireActivity().runOnUiThread(runnable);
            }
        }, 1000);
    }

    private void showError(String message) {
        BackgroundThreadHandler.postOnMainThread(() ->
                new AlertDialog.Builder(this.getContext())
                        .setTitle("Error")
                        .setMessage(message)
                        .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                            dialog.dismiss();
                            mViewModel.navigateBack();
                        })
                        .show());

    }

}
