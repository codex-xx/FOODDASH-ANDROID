package com.example.fooddash;

import android.content.Context;
import android.text.TextUtils;

import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;
    private static Context staticAppContext = null;

    public static void init(Context context) {
        if (context != null && staticAppContext == null) {
            staticAppContext = context.getApplicationContext();
        }
    }

    public static Retrofit getClient(Context context) {
        init(context);
        if (retrofit == null) {
            OkHttpClient client = getUnsafeOkHttpClient();

            retrofit = new Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit;
    }

    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            // Add Logging Interceptor
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(loggingInterceptor);

            // Add Auth Interceptor
            builder.addInterceptor(chain -> {
                if (staticAppContext != null) {
                    String token = AuthSessionManager.getValidAccessTokenOrNull(staticAppContext);
                    if (!TextUtils.isEmpty(token)) {
                        Request newRequest = chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer " + token)
                                .build();
                        return chain.proceed(newRequest);
                    }
                }
                return chain.proceed(chain.request());
            });

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ApiService getApiService() {
        return getClient(null).create(ApiService.class);
    }

    public static ApiService getApiService(Context context) {
        return getClient(context).create(ApiService.class);
    }
}
