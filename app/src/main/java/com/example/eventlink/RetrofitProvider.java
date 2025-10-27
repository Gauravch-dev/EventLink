package com.example.eventlink;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitProvider {
    private static volatile BotService API;

    private RetrofitProvider() {}

    public static BotService getApi() {
        if (API == null) {
            synchronized (RetrofitProvider.class) {
                if (API == null) {
                    HttpLoggingInterceptor log = new HttpLoggingInterceptor();
                    log.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(log)
                            .build();

                    Retrofit r = new Retrofit.Builder()
                            .baseUrl("https://generativelanguage.googleapis.com/")
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(client)
                            .build();

                    API = r.create(BotService.class);
                }
            }
        }
        return API;
    }
}
