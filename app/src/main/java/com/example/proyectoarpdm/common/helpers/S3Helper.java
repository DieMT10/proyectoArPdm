package com.example.proyectoarpdm.common.helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class S3Helper {
    private static final String TAG = "S3Helper";
    private static final String ACCESS_KEY = "YOUR_ACCESS_KEY";
private static final String SECRET_KEY = "YOUR_SECRET_KEY";
    private static final String BUCKET_NAME = "cobro-smart-s3test";
    private static final String REGION_NAME = "us-east-2"; // Para la URL

    public interface UploadCallback {
        void onProgress(int id, long bytesCurrent, long bytesTotal);
        void onSuccess(String url);
        void onError(Exception e);
    }

    public static void uploadFile(Context context, Uri uri, String folder, String fileName, UploadCallback callback) {
        try {
            // Inicializar el manejador de pérdida de red (Requerido por el SDK)
            TransferNetworkLossHandler.getInstance(context);

            AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
            s3Client.setRegion(com.amazonaws.regions.Region.getRegion(Regions.US_EAST_2));

            TransferUtility transferUtility = TransferUtility.builder()
                    .context(context)
                    .s3Client(s3Client)
                    .build();

            File tempFile = copyUriToTempFile(context, uri, fileName);
            String s3Path = "pdm/" + folder + "/" + fileName;

            // Para que los archivos sean públicos, debes configurar una "Bucket Policy" en la consola de AWS S3.
            TransferObserver observer = transferUtility.upload(BUCKET_NAME, s3Path, tempFile);

            observer.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (state == TransferState.COMPLETED) {
                        String url = String.format("https://%s.s3.%s.amazonaws.com/%s", BUCKET_NAME, REGION_NAME, s3Path);
                        callback.onSuccess(url);
                        tempFile.delete();
                    } else if (state == TransferState.FAILED) {
                        callback.onError(new Exception("Transferencia fallida"));
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    callback.onProgress(id, bytesCurrent, bytesTotal);
                }

                @Override
                public void onError(int id, Exception ex) {
                    callback.onError(ex);
                }
            });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private static File copyUriToTempFile(Context context, Uri uri, String fileName) throws Exception {
        File file = new File(context.getCacheDir(), fileName);
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        return file;
    }
}
