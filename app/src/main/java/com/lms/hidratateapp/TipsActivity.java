package com.lms.hidratateapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class TipsActivity extends AppCompatActivity {
    private static final String ARTICLE_URL = "https://es.wikipedia.org/wiki/Agua";
    // MediaWiki API: extracto introductorio + imagen miniatura (si existe).
    private static final String API_URL = "https://es.wikipedia.org/w/api.php?" +
            "action=query&format=json&formatversion=2&prop=extracts%7Cpageimages&" +
            "exintro=1&explaintext=1&piprop=thumbnail&pithumbsize=640&titles=Agua";
    private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build();
    private TextView infoText;
    private ImageView infoImage;
    private Call textCall;
    private Call imageCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tips);
        infoText = findViewById(R.id.tvTipsContent);
        infoImage = findViewById(R.id.ivTipsImage);
        Button retry = findViewById(R.id.btnRetryTips);
        Button source = findViewById(R.id.btnOpenTipsSource);
        Button back = findViewById(R.id.btnTipsBack);

        retry.setOnClickListener(v -> loadArticle());
        back.setOnClickListener(v -> finish());
        source.setOnClickListener(v -> {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(ARTICLE_URL));
            if (browser.resolveActivity(getPackageManager()) != null) {
                startActivity(browser);
            } else {
                Toast.makeText(this, R.string.tips_error, Toast.LENGTH_SHORT).show();
            }
        });
        loadArticle();
    }

    private void loadArticle() {
        if (textCall != null) textCall.cancel();
        if (imageCall != null) imageCall.cancel();
        infoText.setText(R.string.tips_loading);
        infoImage.setImageResource(R.drawable.ic_drop);

        Request request = new Request.Builder().url(API_URL)
                .header("User-Agent", "HidrateAPP/1.0 (curso Aplicaciones Moviles)")
                .build();
        textCall = client.newCall(request);
        textCall.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                if (!call.isCanceled()) showLoadError(e);
            }

            @Override public void onResponse(Call call, Response response) {
                try (Response bodyResponse = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("HTTP " + response.code());
                    }
                    JSONObject root = new JSONObject(response.body().string());
                    JSONArray pages = root.getJSONObject("query").getJSONArray("pages");
                    JSONObject article = pages.getJSONObject(0);
                    String extract = article.optString("extract", "").trim();
                    if (extract.isEmpty()) throw new IOException("Artículo sin contenido");
                    JSONObject thumb = article.optJSONObject("thumbnail");
                    String imageUrl = thumb == null ? "" : thumb.optString("source", "");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        infoText.setText(extract);
                        if (imageUrl.startsWith("https://")) loadImage(imageUrl);
                    });
                } catch (IOException | JSONException e) {
                    showLoadError(e);
                }
            }
        });
    }

    private void loadImage(String url) {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", "HidrateAPP/1.0 (curso Aplicaciones Moviles)")
                .build();
        imageCall = client.newCall(request);
        imageCall.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                if (!call.isCanceled()) Log.w("HidrateAPP", "Imagen no disponible", e);
            }
            @Override public void onResponse(Call call, Response response) {
                try (Response result = response) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    // Limitamos memoria: no descargar imágenes de tamaño arbitrario.
                    long length = response.body().contentLength();
                    if (length > MAX_IMAGE_BYTES) return;
                    byte[] bytes;
                    try (InputStream stream = response.body().byteStream()) {
                        bytes = readLimited(stream, MAX_IMAGE_BYTES);
                    }
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) return;
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) infoImage.setImageBitmap(bitmap);
                    });
                } catch (IOException e) {
                    Log.w("HidrateAPP", "Error al cargar imagen", e);
                }
            }
        });
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > maxBytes) throw new IOException("Imagen demasiado grande");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void showLoadError(Exception exception) {
        Log.w("HidrateAPP", "No se pudo obtener información remota", exception);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            infoText.setText(getString(R.string.tips_error) + "\n\n" + getString(R.string.tips_fallback));
        });
    }

    @Override protected void onDestroy() {
        if (textCall != null) textCall.cancel();
        if (imageCall != null) imageCall.cancel();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        super.onDestroy();
    }
}
